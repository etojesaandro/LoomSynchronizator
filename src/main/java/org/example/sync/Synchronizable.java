package org.example.sync;

import java.util.concurrent.locks.ReentrantLock;

public interface Synchronizable<P extends SynchronizationParameters>
{
  ReentrantLock getSynchronizationLock();
  
  void executeSynchronization(P parameters);

}
