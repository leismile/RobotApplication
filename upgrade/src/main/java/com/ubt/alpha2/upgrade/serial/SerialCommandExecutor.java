/*
 *
 *  *
 *  *  *
 *  *  * Copyright (c) 2008-2017 UBT Corporation.  All rights reserved.  Redistribution,
 *  *  *  modification, and use in source and binary forms are not permitted unless otherwise authorized by UBT.
 *  *  *
 *  *
 *
 */

package com.ubt.alpha2.upgrade.serial;

import android.content.Context;
import android.util.SparseArray;

import com.ubt.alpha2.upgrade.utils.LogUtils;
import com.ubtech.utilcode.utils.ConvertUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import hugo.weaving.DebugLog;
import timber.log.Timber;

/**
 * @desc : 串口命令执行控制器
 * @author: Logic
 * @email : logic.peng@ubtech.com
 * @time : 2017/4/18
 * @modifier:
 * @modify_time:
 */
final public class SerialCommandExecutor {
    public static final byte DEFAULT_COMMAND_SID = 1;
    public static final byte DEFAULT_COMMAND_INDEX = 1;
    public static final int MAX_INDEX = 127;
    private ISerialPortOpProxy mProxy;
    private ExecutorService undoThread = Executors.newFixedThreadPool(2);
    private ExecutorService cmdThread = Executors.newFixedThreadPool(5);
    private int[] mUsedIndexs = new int[MAX_INDEX];
    private SparseArray<Set<Byte>> mCmdIndexs = new SparseArray<>();

    public SerialCommandExecutor(Context context, @SerialConstants.SerialType int serialType){
         mProxy = new Alpha2SerialPortOp(context, serialType);
    }

    public void init() throws IOException {
         mProxy.init();
    }

    public void clean() {
         mProxy.clean();
         mProxy = null;
         undoThread.shutdownNow();
         cmdThread.shutdownNow();
         mUsedIndexs = null;
    }

     @DebugLog
     public SerialCmdResult executeCommand(final byte cmd, final byte[] data){
         final int index = getUnusedIndex(cmd);
         if (index == -1){
             SerialCmdResult result = new SerialCmdResult();
             result.setError(SerialConstants.ERR_BUSY);
             return result;
         }
        FutureTask<SerialCmdResult> task = new FutureTask<SerialCmdResult>(new Callable<SerialCmdResult>() {
            @Override
            public SerialCmdResult call() throws Exception {
                final SerialCmdResult result = new SerialCmdResult();
                try {
                    mProxy.sendCommand(DEFAULT_COMMAND_SID, cmd, (byte)index, data, new ISerialCommandCallback() {
                        @Override
                        public void onRcvSerialPortData(byte sessionId, byte cmd, byte index, @SerialConstants.ErrorCode int resultCode, byte[] bytes) {
                            result.setCmd(cmd);
                            result.setError(resultCode);
                            result.setResult(bytes);
                        }
                    });
                }catch (Exception e){
                    Timber.e(e.getMessage());
                }finally {
                    synchronized (SerialCommandExecutor.this){
                        mUsedIndexs[index-1] = 0;
                    }
                }
                //上述方法是阻塞的
                return result;
            }
        });
        cmdThread.execute(task);
        try {
            SerialCmdResult result = task.get();
            return result;
        }  catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    boolean undoCommand(final byte cmd) {
        FutureTask<Boolean> futureTask = new FutureTask(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                boolean ret = true ;
                synchronized (SerialCommandExecutor.this){
                    Set<Byte> indexs = mCmdIndexs.get(cmd);
                    Iterator<Byte> it = indexs.iterator();
                    while (it.hasNext()){
                        ret = mProxy.undoCommand(DEFAULT_COMMAND_SID, cmd, it.next());
                        mUsedIndexs[it.next() -1 ] = 0;
                    }
                    mCmdIndexs.remove(cmd);
                }
                return ret;//这个返回值没啥意义
            }
        });
        undoThread.execute(futureTask);
        try {
            return futureTask.get().booleanValue();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    synchronized int getUnusedIndex(byte cmd){
        for (int i = 1; i <= MAX_INDEX; i++){
            if (mUsedIndexs[i-1] == 0) {
                mUsedIndexs[i-1] = 1;
                Set<Byte> indexs = mCmdIndexs.get(cmd);
                if(indexs == null) {
                    indexs = new HashSet<>();
                    mCmdIndexs.put(cmd, indexs);
                }
                indexs.add((byte)i);
                return i;
            }
        }
        return -1;
    }
}
