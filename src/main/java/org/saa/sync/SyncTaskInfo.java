package org.saa.sync;

import java.util.concurrent.ScheduledFuture;

class SyncTaskInfo
{
  private final ScheduledFuture<?> task;
  private final SynchronizationParameters parameters;
  private final long period;
  
  public SyncTaskInfo(ScheduledFuture<?> task, SynchronizationParameters parameters, long period)
  {
    this.task = task;
    this.parameters = parameters;
    this.period = period;
  }
  
  public ScheduledFuture<?> getTask()
  {
    return task;
  }
  
  public SynchronizationParameters getParameters()
  {
    return parameters;
  }

  public long getPeriod()
  {
    return period;
  }
}