package org.example.sync;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class SynchronizationManager
{
  private static final int WAIT_TIME_IN_MINUTES = 2;
  
  private static final double INITIAL_DELAY_BOUND = 0;
  
  private static final double LAST_INITIAL_DELAY_BOUND = 0.9;

  private final ScheduledExecutorService syncTimersPool;

  private final ExecutorService syncExecutorService;

  private final Synchronizable<SynchronizationParameters> synchronizable;
  
  private final String name;
  
  private SyncTaskInfo mainTask;
  
  private final Map<Long, SyncTaskInfo> additionalTasks = new HashMap<>();
  
  private final ReentrantLock syncLock = new ReentrantLock(true);
  
  // Using set here to avoid having multiple requested tasks with similar parameters
  private final ConcurrentLinkedQueue<SynchronizationParameters> pending = new ConcurrentLinkedQueue<>();
  
  private final AtomicBoolean hasScheduledTask = new AtomicBoolean(false);
  private volatile boolean started;

  public SynchronizationManager(String name, ScheduledExecutorService syncTimersPool, ExecutorService syncExecutorService, Synchronizable<SynchronizationParameters> synchronizable) {
    super();
    this.name = name;
    this.syncTimersPool = syncTimersPool;
    this.syncExecutorService = syncExecutorService;
    this.synchronizable = synchronizable;
  }
  
  public void start(SynchronizationParameters parameters, long mainSyncPeriod, long firstSyncDelay)
  {
    scheduleMainSyncTask(parameters, mainSyncPeriod, firstSyncDelay);
    started = true;
  }
  
  private void scheduleMainSyncTask(SynchronizationParameters parameters, long mainSyncPeriod, long firstSyncDelay)
  {
    Runnable mt = createSyncTask(parameters);

    // This delay prevents the situation when all the tasks are executing at once and cause the traffic jam in the queue.
    long delay = (long) (mainSyncPeriod * ThreadLocalRandom.current().nextDouble(INITIAL_DELAY_BOUND, LAST_INITIAL_DELAY_BOUND));

    ScheduledFuture future = syncTimersPool.scheduleAtFixedRate(mt, firstSyncDelay + delay, mainSyncPeriod, TimeUnit.MILLISECONDS);
    mainTask = new SyncTaskInfo(future, parameters, mainSyncPeriod);
  }
  
  private SyncTask createSyncTask(SynchronizationParameters parameters)
  {
    return new SyncTask(parameters);
  }
  
  public void stop()
  {
    started = false;
    boolean locked = false;
    try
    {
      locked = syncLock.tryLock(WAIT_TIME_IN_MINUTES, TimeUnit.MINUTES);

      for (Entry<Long, SyncTaskInfo> entry : new LinkedHashSet<>(additionalTasks.entrySet()))
      {
        cancel(entry.getValue());
      }
      if (mainTask != null)
      {
        cancel(mainTask);
      }
    }
    catch (InterruptedException e)
    {
      throw new RuntimeException(e);
    }
    finally
    {
      if (locked)
      {
        syncLock.unlock();
      }
    }
  }
  
  private void cancel(SyncTaskInfo taskInfo)
  {
    if (taskInfo.getTask() != null)
    {
      taskInfo.getTask().cancel(true);
    }
    if (syncExecutorService instanceof ThreadPoolExecutor tpe)
    {
      tpe.remove((Runnable) taskInfo.getTask());
    }
    additionalTasks.remove(taskInfo.getPeriod());
  }

  private SyncTaskInfo scheduleSyncTask(SynchronizationParameters parameters, long newPeriod)
  {
    SyncTask st = createSyncTask(parameters);
    long delay = (long) (newPeriod * ThreadLocalRandom.current().nextDouble(INITIAL_DELAY_BOUND, LAST_INITIAL_DELAY_BOUND));
    
    ScheduledFuture<?> future = syncTimersPool.scheduleAtFixedRate(st, delay, newPeriod, TimeUnit.MILLISECONDS);
    return new SyncTaskInfo(future, parameters, newPeriod);
  }
  
  public void requestSynchronization(SynchronizationParameters parameters)
  {
    // To prevent tasks adding to the pending set in case the server is stopping
    if (!started)
    {
      return;
    }
    synchronized (hasScheduledTask)
    {
      if (syncLock.isLocked() || hasScheduledTask.get())
      {
        if (!pending.contains(parameters))
        {
          pending.add(parameters);
        }
      }
      else
      {
        hasScheduledTask.set(true);
        syncExecutorService.submit(new Synchronizer(parameters));
      }
    }
  }

  public SyncTaskInfo scheduleTask(SynchronizationParameters parameters, Long period)
  {
    SyncTaskInfo newTask = scheduleSyncTask(parameters, period);
    additionalTasks.put(period, newTask);
    return newTask;
  }


  private void executeSynchronization(SynchronizationParameters initialParameters)
  {
    syncLock.lock();
    try
    {
      hasScheduledTask.set(false);
      
      executeSynchronizationImpl(initialParameters);
      
      SynchronizationParameters pendingParameters;
      
      while ((pendingParameters = pending.poll()) != null)
      {
        if (!started)
        {
          break;
        }
        executeSynchronizationImpl(pendingParameters);
      }
    }
    catch (Exception ex)
    {
      System.out.println("Unexpected error during synchronization");
    }
    finally
    {
      syncLock.unlock();
    }
  }
  
  private void executeSynchronizationImpl(SynchronizationParameters parameters)
  {
    String originalThreadName = Thread.currentThread().getName();
    synchronizable.getSynchronizationLock().lock();
    try
    {
      Thread.currentThread().setName("Synchronizer: " + synchronizable);
      synchronizable.executeSynchronization(parameters);
    }
    finally
    {
      synchronizable.getSynchronizationLock().unlock();
      Thread.currentThread().setName(originalThreadName);
    }
  }
  
  private class SyncTask implements Runnable
  {
    private final SynchronizationParameters parameters;
    
    public SyncTask(SynchronizationParameters parameters)
    {
      this.parameters = parameters;
    }
    
    @Override
    public void run()
    {
      requestSynchronization(parameters);
    }
  }
  
  private class Synchronizer implements Runnable
  {
    private final SynchronizationParameters parameters;
    
    public Synchronizer(SynchronizationParameters parameters)
    {
      this.parameters = parameters;
    }
    
    @Override
    public void run()
    {
      if (!started)
      {
        return;
      }
      
      if (syncLock.isLocked())
      {
        if (!pending.contains(parameters))
        {
          pending.add(parameters);
        }
      }
      else
      {
        executeSynchronization(parameters);
      }
    }
  }
}
