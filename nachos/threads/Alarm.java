package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		long curTime = Machine.timer().getTime();

		while(!threadQueue.isEmpty() && threadQueue.peek().getWaitUntilTime() <= curTime) {
			threadQueue.poll().ready();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 *
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * @param x the minimum number of clock ticks to wait.
	 *
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		if(x<=0) return;
		// disable interrupt in this procedure
		boolean intStatus = Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime()+x;
		KThread.currentThread().setWaitUntilTime(wakeTime);
		threadQueue.add(KThread.currentThread());

		KThread.sleep();
		Machine.interrupt().restore(intStatus);
	}

	public static void alarmTest1() {
		int durations[] = {50, 100, 100};
		long t0, t1;

		for (int d : durations) {

			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println (KThread.currentThread().getName()+" alarmTest1: waited for " + (t1 - t0) + " ticks");
		}
	}

	// Implement more test methods here ...
	public static KThread alarmTest2(String name, long time) {
		KThread t = new KThread(new Runnable(){
			@Override
			public void run() {
				for(int i = 0; i < 6; i++)
				{
					long t0, t1;
					t0 = Machine.timer().getTime();
					ThreadedKernel.alarm.waitUntil (time*(i+1));
					t1 = Machine.timer().getTime();
					System.out.println (KThread.currentThread().getName()+" alarmTest2: waited for " + (t1 - t0) + " ticks");
				}
			}
		}).setName(name);

		return t;
	}

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		KThread tA = alarmTest2("Thread A", 1000);
		KThread tB= alarmTest2("Thread B",500);
		//	alarmTest1();
		tA.fork();
		tB.fork();
		KThread.currentThread().yield();
		alarmTest1();
		tA.join();
//		tA.join();
		tB.join();


	}


	private static PriorityQueue<KThread> threadQueue = new PriorityQueue<KThread>(new Comparator<KThread>() {
		@Override
		public int compare(KThread t0, KThread t1) {
			if(t0.getWaitUntilTime() > t1.getWaitUntilTime()) return 1;
			else if (t0.getWaitUntilTime() < t1.getWaitUntilTime()) return -1;
			else return 0;
		}
	});
}
