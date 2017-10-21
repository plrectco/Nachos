package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	private int hasSpeaker;
	private int hasListener;
	private Lock speakerLock;
	private Lock listenerLock;
	private Condition speakerCondition;
	private Condition listenerCondition;
	private Lock comLock;
	private Condition comCondition;
	private int message;
	private int hasDone;
	public Communicator() {
		hasSpeaker = 0;
		hasListener = 0;
		speakerLock = new Lock();
		speakerCondition = new Condition(speakerLock);
		listenerLock = new Lock();
		listenerCondition = new Condition(listenerLock);
		comLock = new Lock();
		comCondition = new Condition(comLock);
		hasDone = 0;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		// block other speakers
		speakerLock.acquire();
//		while(hasSpeaker > 0)
//			speakerCondition.sleep();


		// block listener
		comLock.acquire();
		hasSpeaker++;
		message = word;

		while(hasListener == 0){
			comCondition.sleep();
		}

		if(hasDone == 0)
			comCondition.wake();
		else
			hasDone = 0;
		hasListener--;
//		listenerCondition.wake();
		comLock.release();

		speakerLock.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		listenerLock.acquire();
//		while(hasListener > 0)
//			listenerCondition.sleep();

		comLock.acquire();
		hasListener++;
		while(hasSpeaker == 0 || hasDone == 1) {
			comCondition.sleep();
		}
		int returnMessage = message;
		hasDone = 1;
		comCondition.wake();

		hasSpeaker--;
//		speakerCondition.wake();
		comLock.release();

		listenerLock.release();

		return returnMessage;
	}

	// Place Communicator test code inside of the Communicator class.

	// A more complex test program for Communicator.  Do not use this
	// test program as your first Communicator test.  First test it
	// with more basic test programs to verify specific functionality,
	// and then try this test program.

	public static void commTest6() {
		final Communicator com = new Communicator();
		final long times[] = new long[4];
		final int words[] = new int[2];
		KThread speaker1 = new KThread( new Runnable () {
			public void run() {
				com.speak(4);
				times[0] = Machine.timer().getTime();
			}
		});
		speaker1.setName("S1");
		KThread speaker2 = new KThread( new Runnable () {
			public void run() {
				com.speak(7);
				times[1] = Machine.timer().getTime();
			}
		});
		speaker2.setName("S2");
		KThread listener1 = new KThread( new Runnable () {
			public void run() {
				times[2] = Machine.timer().getTime();
				words[0] = com.listen();
			}
		});
		listener1.setName("L1");
		KThread listener2 = new KThread( new Runnable () {
			public void run() {
				times[3] = Machine.timer().getTime();
				words[1] = com.listen();
			}
		});
		listener2.setName("L2");

		speaker1.fork(); speaker2.fork(); listener1.fork(); listener2.fork();
		speaker1.join(); speaker2.join(); listener1.join(); listener2.join();

		Lib.assertTrue(words[0] == 4, "Didn't listen back spoken word.");
		Lib.assertTrue(words[1] == 7, "Didn't listen back spoken word.");
		Lib.assertTrue(times[0] > times[2], "speak() returned before listen() called.");
		Lib.assertTrue(times[1] > times[3], "speak() returned before listen() called.");
		System.out.println("commTest6 successful!");
	}

	// Invoke Communicator.selfTest() from ThreadedKernel.selfTest()

	public static void selfTest() {
		// place calls to simpler Communicator tests that you implement here

		commTest6();
	}
}
