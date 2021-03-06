package com.istat.freedev.processor.interfaces;

import com.istat.freedev.processor.Process;

/**
 * Created by istat on 14/10/16.
 */

public interface ProcessListener {
    void onProcessEnqueued(Process process, String id);

    void onProcessStarted(Process process, String id);

    void onProcessStateChanged(Process process, String id, int state);

    void onProcessFinished(Process process, String id);

}
