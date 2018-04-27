package com.istat.freedev.processor;

import android.text.TextUtils;

import com.istat.freedev.processor.interfaces.ProcessCallback;
import com.istat.freedev.processor.utils.ProcessTools;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by istat on 04/10/16.
 */

public abstract class Process<Result, Error extends Throwable> {
    public final static int FLAG_SYS_DEFAUlT = 0;
    public final static int FLAG_SYS_CANCELABLE = 1;
    public final static int FLAG_USER_CANCELABLE = 1;
    public final static int FLAG_BACKGROUND = 2;
    public final static int FLAG_DETACHED = 3;
    int flag;
    public final static int PROMISE_WHEN_SUCCESS = 0,
            PROMISE_WHEN_ERROR = 1,
            PROMISE_WHEN_FAIL = 2,
            PROMISE_WHEN_ANYWAY = 3,
            PROMISE_WHEN_ABORTED = 4,
            PROMISE_WHEN_STARTED = 5;
    Result result;
    Error error;
    Throwable exception;
    String id;
    final ConcurrentLinkedQueue<ProcessCallback<Result, Error>> processCallbacks = new ConcurrentLinkedQueue();
    private long startingTime = -1, completionTime = -1;
    private Object[] executionVariableArray = new Object[0];
    ProcessManager manager;
    int state;
    boolean canceled;

    public ProcessManager getManager() {
        return manager;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public void addCallback(ProcessCallback<Result, Error> executionListener) {
        if (executionListener != null) {
            this.processCallbacks.add(executionListener);
        }
    }

    public ExecutionVariables getExecutionVariables() {
        return new ExecutionVariables();
    }

    public <T> T getExecutionVariable(int index) {
        return getExecutionVariables().getVariable(index);
    }

    final void execute(ProcessManager manager, Object... vars) {
        this.manager = manager;
        geopardise = false;
        try {
            this.executionVariableArray = vars;
            startingTime = System.currentTimeMillis();
//            memoryRunnableTask.putAll(runnableTask);
            onExecute(getExecutionVariables());
            notifyStarted();
        } catch (Exception e) {
            notifyStarted();
            notifyFailed(e);
        }
    }

    final void reset() {
        geopardise = false;
        executedRunnable.clear();
//        runnableTask.putAll(memoryRunnableTask);
        try {
            onExecute(getExecutionVariables());
        } catch (Exception e) {
            notifyStarted();
            notifyFailed(e);
        }
    }

    protected abstract void onExecute(ExecutionVariables executionVariables);

    protected abstract void onResume();

    protected abstract void onPaused();

    protected abstract void onStopped();

    protected abstract void onCancel();

    public abstract boolean isRunning();

    public abstract boolean isCompleted();

    public abstract boolean isPaused();

    public boolean isCanceled() {
        return canceled;
    }

    protected void onRestart(int mode) {
    }

    public Error getError() {
        return error;
    }

    public Throwable getFailCause() {
        return exception;
    }

    public <T> T getErrortAs(Class<T> cLass) {
        if (error != null) {
            if (cLass.isAssignableFrom(error.getClass())) {
                return (T) error;
            }
            if (CharSequence.class.isAssignableFrom(cLass)) {
                return (T) error.toString();
            }
        }
        return null;
    }

    public <T extends Error> T optError() {
        try {
            return (T) error;
        } catch (Exception e) {
            return null;
        }
    }

    public Result getResult() {
        return result;
    }

    public <T> T getResultAs(Class<T> cLass) {
        if (result != null) {
            if (cLass.isAssignableFrom(result.getClass())) {
                return (T) result;
            }
            if (CharSequence.class.isAssignableFrom(cLass)) {
                return (T) result.toString();
            }
        }
        return null;
    }

    public <T> T optResult() {
        try {
            return (T) result;
        } catch (Exception e) {
            return null;
        }
    }

    public final boolean hasResult() {
        return result != null;
    }

    protected final void setResult(Result result) {
        this.result = result;
    }

    public final String getId() {
        return id;
    }

    final void setId(String id) {
        this.id = id;
    }

    public final void pause() {
        onPaused();
    }

    public final void resume() {
        onResume();
    }

    public final void restart() {
        restart(RESTART_MODE_ABORT);
    }

    public final static int RESTART_MODE_GEOPARDISE = 0, RESTART_MODE_ABORT = 1;
    private final static int TIME_MILLISEC_WAIT_FOR_RESTART = 100;

    public final void restart(int mode) {
        onRestart(mode);
        if (isRunning()) {
            if (RESTART_MODE_GEOPARDISE == mode) {
                geopardise = true;
            }
            cancel();
        }
//        final Object[] executionVars = this.executionVariableArray;
        getManager().postDelayed(new Runnable() {
            @Override
            public void run() {
//                execute(Process.getManager(), executionVars);
                reset();
            }
        }, TIME_MILLISEC_WAIT_FOR_RESTART);

    }


    public final void stop() {
        onStopped();
    }

    public final boolean cancel() {
        boolean running = isRunning();
        if (running) {
            canceled = true;
            onCancel();
            notifyAborted();
        }
        return running;
    }

//    public boolean compromise(int When) {
//        boolean removed = runnableTask.contains(When);
//        runnableTask.remove(When);
//        return removed;
//    }

    public final boolean compromise(int... when) {
        boolean running = isRunning();
        if (when == null || when.length == 0) {
            when = new int[]{PROMISE_WHEN_ABORTED, PROMISE_WHEN_ANYWAY, PROMISE_WHEN_ERROR, PROMISE_WHEN_FAIL, PROMISE_WHEN_STARTED, PROMISE_WHEN_SUCCESS};
        }
        for (int i : when) {
            if (runnableTask.containsKey(i)) {
                try {
                    runnableTask.get(i).clear();
                } catch (Exception e) {

                } finally {
                    runnableTask.remove(i);
                }
            }
        }
        return running;
    }

    boolean geopardise = false;

    public final boolean hasBeenGeopardise() {
        return geopardise;
    }

    public final boolean geopardise() {
        geopardise = true;
        compromise();
        boolean cancelled = cancel();
        return cancelled;

    }

    boolean hasId() {
        return !TextUtils.isEmpty(getId());
    }


    final ConcurrentLinkedQueue<Runnable> executedRunnable = new ConcurrentLinkedQueue<Runnable>();
    //    final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Runnable>> memoryRunnableTask = new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Runnable>>();
    final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Runnable>> runnableTask = new ConcurrentHashMap();

    public <T extends Process> T promise(Runnable runnable, int... when) {
//        if(!isRunning()){
//            throw new IllegalStateException("Oups, current Process is not running. It has to be running before adding any promise or promise");
//        }
        for (int value : when) {
            addFuture(runnable, value);
        }
        return (T) this;
    }

    public <T extends Process> T then(final PromiseCallback<Result> promise) {
//        if(!isRunning()){
//            throw new IllegalStateException("Oups, current Process is not running. It has to be running before adding any promise or promise");
//        }
        addFuture(new Runnable() {
            @Override
            public void run() {
                promise.onPromise(getResult());
            }
        }, PROMISE_WHEN_SUCCESS);
        return (T) this;
    }

    public <T extends Process> T error(final PromiseCallback<Error> promise) {
//        if(!isRunning()){
//            throw new IllegalStateException("Oups, current Process is not running. It has to be running before adding any promise or promise");
//        }
        addFuture(new Runnable() {
            @Override
            public void run() {
                promise.onPromise(getError());
            }
        }, PROMISE_WHEN_ERROR);
        return (T) this;
    }

    public <T extends Process> T failed(final PromiseCallback<Throwable> promise) {
//        if(!isRunning()){
//            throw new IllegalStateException("Oups, current Process is not running. It has to be running before adding any promise or promise");
//        }
        addFuture(new Runnable() {
            @Override
            public void run() {
                promise.onPromise(getFailCause());
            }
        }, PROMISE_WHEN_FAIL);
        return (T) this;
    }

    public <T extends Process> T thrOws(final PromiseCallback<Throwable> promise) {
//        if(!isRunning()){
//            throw new IllegalStateException("Oups, current Process is not running. It has to be running before adding any promise or promise");
//        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Throwable error = getFailCause() != null ? getFailCause() : getError();
//                if (error == null) {
//                    error = new InterruptedException("Process has been canceled");
//                }
                promise.onPromise(error);
            }
        };
        addFuture(runnable, PROMISE_WHEN_FAIL);
        addFuture(runnable, PROMISE_WHEN_ERROR);
//        addFuture(runnable, PROMISE_WHEN_ABORTED);
        return (T) this;
    }

    public <T extends Process> T abortion(final PromiseCallback<Void> promise) {
//        if(!isRunning()){
//            throw new IllegalStateException("Oups, current Process is not running. It has to be running before adding any promise or promise");
//        }
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                promise.onPromise(null);
            }
        };
        addFuture(runnable, PROMISE_WHEN_ABORTED);
        return (T) this;
    }

    public interface PromiseCallback<T> {
        void onPromise(T data);
    }


    public <T extends Process> T sendMessage(final MessageCarrier message, int... when) {
        return sendMessage(message, new Object[0], when);
    }

    public <T extends Process> T sendMessage(final MessageCarrier carrier, final Object[] messages, int... when) {
        for (int value : when) {
            addFuture(new Runnable() {
                @Override
                public void run() {
                    carrier.process = Process.this;
                    carrier.handleMessage(messages);
                }
            }, value);
        }
        return (T) this;
    }

    private void addFuture(Runnable runnable, int conditionTime) {
        if (!isFutureContain(runnable, conditionTime)) {
            ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(conditionTime);
            if (runnableList == null) {
                runnableList = new ConcurrentLinkedQueue();
            }
            runnableList.add(runnable);
            runnableTask.put(conditionTime, runnableList);
        }
    }

    private boolean isFutureContain(Runnable run, int conditionTime) {
        ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(conditionTime);
        if (runnableList == null || runnableList.isEmpty()) {
            return false;
        }
        return runnableList.contains(run);
    }

    public final long getStartingTime() throws ProcessManager.ProcessException {
        if (startingTime < 0) {
            throw new ProcessManager.ProcessException("Oups, it seem than this process is not yet started.");
        }
        return startingTime;
    }

    public final long getCompletionTime() throws ProcessManager.ProcessException {
        if (completionTime < 0) {
            throw new ProcessManager.ProcessException("Oups, it seem than this process is not yet completed.");
        }
        return completionTime;
    }

    public final long getLinvingTime() {
        if (startingTime < 0) {
            return 0;
        }
        return System.currentTimeMillis() - startingTime;
    }

    private void executeWhen(ConcurrentLinkedQueue<Runnable> runnableList) {
        if (!geopardise && runnableList != null && runnableList.size() > 0) {
            for (Runnable runnable : runnableList) {
                if (!executedRunnable.contains(runnable)) {
                    runnable.run();
                    executedRunnable.add(runnable);
                }
            }
        }
    }

    protected void onCompleted(boolean state, Result result, Error error) {

    }

    protected void onSucceed(Result result) {

    }

    protected void onError(Error error) {

    }

    protected void onFailed(Throwable e) {

    }

    protected void onAborted() {

    }

    protected void onFinished() {

    }

    final void notifyStarted() {
        if (!geopardise) {
            Process.this.state = PROMISE_WHEN_STARTED;
            post(new Runnable() {
                @Override
                public void run() {
                    if (getManager() != null) {
                        getManager().notifyProcessStarted(Process.this/*, getExecutionVariables().asArray()*/);
                    }
                    for (ProcessCallback<Result, Error> executionListener : processCallbacks) {
                        executionListener.onStart(/*Process.this*/);
                    }
                    ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(PROMISE_WHEN_STARTED);
                    executeWhen(runnableList);
                }
            });

        }
    }

    final void notifyCompleted(boolean state) {
        if (!geopardise) {
            if (getManager() != null) {
                getManager().notifyProcessCompleted(this);
            }
            for (ProcessCallback<Result, Error> executionListener : processCallbacks) {
                executionListener.onCompleted(/*this,*/ this.result, state);
            }
            executedRunnable.clear();
            ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(PROMISE_WHEN_ANYWAY);
            executeWhen(runnableList);
            onCompleted(state, result, error);
            onFinished();
        }
    }

    protected final void notifySucceed(final Result result) {
        if (!geopardise) {
            this.state = PROMISE_WHEN_SUCCESS;
            this.result = result;
            post(new Runnable() {
                @Override
                public void run() {
                    notifyCompleted(true);
                    for (ProcessCallback<Result, Error> executionListener : processCallbacks) {
                        executionListener.onSuccess(/*Process.this,*/ result);
                    }
                    ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(PROMISE_WHEN_SUCCESS);
                    executeWhen(runnableList);
                    onSucceed(result);
                }
            });
        }
    }

    protected final void notifyError(final Error error) {
        if (!geopardise) {
            this.state = PROMISE_WHEN_ERROR;
            this.error = error;
            post(new Runnable() {
                @Override
                public void run() {
                    notifyCompleted(false);
                    for (ProcessCallback<Result, Error> executionListener : processCallbacks) {
                        executionListener.onError(/*Process.this, */error);
                    }
                    ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(PROMISE_WHEN_ERROR);
                    executeWhen(runnableList);
                    onError(error);
                }
            });
        }
    }

    protected final void notifyFailed(final Throwable e) {
        if (!geopardise) {
            this.state = PROMISE_WHEN_FAIL;
            this.exception = e;
            post(new Runnable() {
                @Override
                public void run() {
                    notifyCompleted(false);
                    for (ProcessCallback<Result, Error> executionListener : processCallbacks) {
                        executionListener.onFail(/*Process.this,*/ e);
                    }
                    ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(PROMISE_WHEN_FAIL);
                    executeWhen(runnableList);
                    onFailed(e);
                }
            });
        }
    }

    protected final void notifyAborted() {
        if (!geopardise) {
            this.state = PROMISE_WHEN_ABORTED;
            post(new Runnable() {
                @Override
                public void run() {
                    for (ProcessCallback<Result, Error> executionListener : processCallbacks) {
                        executionListener.onAborted(/*Process.this*/);
                    }
                    ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(PROMISE_WHEN_ABORTED);
                    executeWhen(runnableList);
                    onAborted();
                    onFinished();
                }
            });
        }
    }

    protected final void notifyDelayedAborted(int delay) {
        if (delay <= 0) {
            notifyAborted();
        } else {
            manager.postDelayed(new Runnable() {
                @Override
                public void run() {
                    notifyAborted();
                }
            }, delay);
        }
    }

    protected final void notifyDelayedFailed(final Exception e, int delay) {
        if (delay <= 0) {
            notifyFailed(e);
        } else {
            manager.postDelayed(new Runnable() {
                @Override
                public void run() {
                    notifyFailed(e);
                }
            }, delay);
        }
    }

    protected final void notifyDelayedError(final Error error, int delay) {
        if (delay <= 0) {
            notifyError(error);
        } else {
            manager.postDelayed(new Runnable() {
                @Override
                public void run() {
                    notifyError(error);
                }
            }, delay);
        }
    }

    protected final void notifyDelayedSuccess(final Result result, int delay) {
        if (delay <= 0) {
            notifySucceed(result);
        } else {
            manager.postDelayed(new Runnable() {
                @Override
                public void run() {
                    notifySucceed(result);
                }
            }, delay);
        }
    }

    public boolean hasError() {
        return error != null;
    }

    public boolean isFailed() {
        return exception != null;
    }

    public boolean hasSucceed() {
        return !hasError() && !isFailed();
    }

    public void attach(ProcessCallback<Result, Error> callback) {
        ProcessTools.attachToProcessCycle(this, callback);
    }

    public int cancelAllCallback() {
        int callbackCount = processCallbacks.size();
        processCallbacks.clear();
        return callbackCount;
    }

    public boolean cancelCallback(ProcessCallback callback) {
        boolean removed = processCallbacks.contains(callback);
        if (removed) {
            processCallbacks.remove(callback);
        }
        return removed;
    }

    public boolean compromise(Runnable runnable) {
        Iterator<Integer> iterator = runnableTask.keySet().iterator();
        while (iterator.hasNext()) {
            Integer when = iterator.next();
            ConcurrentLinkedQueue<Runnable> runnables = runnableTask.get(when);
            if (runnables != null) {
                boolean removed = runnables.contains(runnable);
                if (removed) {
                    runnables.remove(runnable);
                    return true;
                }
            }
        }
        return false;
    }

    public abstract static class MessageCarrier {
        List<Object> messages;
        Process process;

        void handleMessage(Object... messages) {
            Collections.addAll(this.messages, messages);
            onHandleMessage(process, messages);
        }

        public abstract void onHandleMessage(Process process, Object... messages);

        public List<Object> getMessages() {
            return messages;
        }
    }

    public class ExecutionVariables {
        public int getCount() {
            return executionVariableArray.length;
        }

        public Object[] asArray() {
            return executionVariableArray;
        }

        public List<?> asList() {
            return Arrays.asList(executionVariableArray);
        }

        public <T> T getVariable(int index) {
            if (executionVariableArray.length <= index) {
                return null;
            }
            try {
                return (T) executionVariableArray[index];
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        public boolean isVarInstanceOf(int index, Class<?> cLass) {
            Object var = getVariable(index);
            return var != null && cLass != null && cLass.isAssignableFrom(var.getClass());
        }

        public <T> T getVariable(int index, Class<T> cLass) throws ArrayIndexOutOfBoundsException {
            if (executionVariableArray.length <= index) {
                throw new ArrayIndexOutOfBoundsException("executionVariables length=" + executionVariableArray.length + ", requested index=" + index
                );
            }
            Object var = executionVariableArray[index];
            if (var == null) {
                return null;
            }
            if (cLass.isAssignableFrom(var.getClass())) {
                return (T) var;
            } else {
                throw new IllegalArgumentException("Item at index=" + index + " has type class=" + var.getClass() + ", requested class=" + cLass);
            }
        }

        public String getStringVariable(int index) throws ArrayIndexOutOfBoundsException {
            if (executionVariableArray.length <= index) {
                throw new ArrayIndexOutOfBoundsException("executionVariables length=" + executionVariableArray.length + ", requested index=" + index
                );
            }
            Object var = executionVariableArray[index];
            if (var == null) {
                return null;
            }
            return String.valueOf(var);
        }

        public int getIntVariable(int index) throws ArrayIndexOutOfBoundsException {
            if (executionVariableArray.length <= index) {
                throw new ArrayIndexOutOfBoundsException("executionVariables length=" + executionVariableArray.length + ", requested index=" + index
                );
            }
            Object var = executionVariableArray[index];
            if (var == null) {
                return 0;
            }
            return Integer.valueOf(String.valueOf(var));
        }

        public long getLongVariable(int index) throws ArrayIndexOutOfBoundsException {
            if (executionVariableArray.length <= index) {
                throw new ArrayIndexOutOfBoundsException("executionVariables length=" + executionVariableArray.length + ", requested index=" + index
                );
            }
            Object var = executionVariableArray[index];
            if (var == null) {
                return 0;
            }
            return Long.valueOf(String.valueOf(var));
        }

        public float getFloatVariable(int index) throws ArrayIndexOutOfBoundsException {
            if (executionVariableArray.length <= index) {
                throw new ArrayIndexOutOfBoundsException("executionVariables length=" + executionVariableArray.length + ", requested index=" + index
                );
            }
            Object var = executionVariableArray[index];
            if (var == null) {
                return 0;
            }
            return Float.valueOf(String.valueOf(var));
        }

        public double getDoubleVariable(int index) throws ArrayIndexOutOfBoundsException {
            if (executionVariableArray.length <= index) {
                throw new ArrayIndexOutOfBoundsException("executionVariables length=" + executionVariableArray.length + ", requested index=" + index
                );
            }
            Object var = executionVariableArray[index];
            if (var == null) {
                return 0;
            }
            return Double.valueOf(String.valueOf(var));
        }

        public int length() {
            return executionVariableArray.length;
        }

    }

    public int getState() {
        return state;
    }

    protected void post(Runnable runnable) {
        getManager().post(runnable);
    }
}
