package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		this.waitQueue = new LinkedList<>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		conditionLock.release();
		boolean intStatus = Machine.interrupt().disable();
		waitQueue.push(KThread.currentThread());
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		if (!waitQueue.isEmpty())
		{
			boolean intStatus = Machine.interrupt().disable();

			KThread thread = waitQueue.pop();
			thread.ready();

			Machine.interrupt().restore(intStatus);
		}
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		while (!waitQueue.isEmpty())
			wake();
	}

	private Lock conditionLock;
	private LinkedList<KThread> waitQueue;

	public static void initTest(){
		ResourceTest R = new ResourceTest();
		Runnable target = new Runnable() {

			@Override
			public void run() {
				for(int i = 0; i < 12; i++)
				{
					 R.withDraw();

				}

			}
		};

		KThread threadA = new KThread(target).setName("A");
		KThread threadB = new KThread(target).setName("B");
		KThread threadC = new KThread(new Runnable() {
			@Override
			public void run() {
				for(int i = 0; i < 20; i++)
				{
					R.putMoney();
					if(i%5==0)
						R.putLotsMoney();

				}
			}
		}).setName("C");
		threadA.fork();
		threadB.fork();
		threadC.fork();


		threadA.join();
		threadB.join();
		threadC.join();



	}
}

class ResourceTest {
	public static Lock resource = new Lock();
	public static Condition2 cond = new Condition2(resource);
	public static int money = 0;
	void withDraw() {
		resource.acquire();
		while(money <= 0)
			cond.sleep();
		System.out.println(KThread.currentThread().getName()+" Withdrawing "+ money);
		money = 0;
		cond.wake();
		resource.release();
	}
	void putMoney() {
		resource.acquire();
		while(money > 0)
			cond.sleep();
		money++;
		cond.wake();
		resource.release();
	}

	void putLotsMoney() {
		resource.acquire();
		while(money > 0 )
			cond.sleep();
		money += 2;
		cond.wakeAll();
		resource.release();
	}
}