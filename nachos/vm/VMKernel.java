package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.LinkedList;
import java.util.Queue;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swpFile = fileSystem.open(swpName, true);
		pinned = new boolean[numPhyPages];
		ppn2Process = new VMProcess[numPhyPages];
		ppn2vpn = new int[numPhyPages];

	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swpFile.close();
		fileSystem.remove(swpName);
		super.terminate();
	}

	/**
	 * ppn should be valid
	 * require p and vpn to be valid
	 * @param ppn
	 * @param p
	 * @param vpn
	 */
	public static void setInvertTable(int ppn, VMProcess p, int vpn) {
		Lib.assertTrue(ppn >= 0 && ppn < numPhyPages);
		ppn2Process[ppn] = p;
		ppn2vpn[ppn] = vpn;
		pinned[ppn] = false;
	}

	public static void removeInvertTableMap(int ppn) {
		ppn2vpn[ppn] = -1;
		ppn2Process[ppn] = null;
		pinned[ppn] = false;
	}

	public static VMProcess getVMProcess(int ppn) {
		Lib.assertTrue(ppn >= 0 && ppn < numPhyPages);
		return ppn2Process[ppn];
	}

	public static int getvpn(int ppn) {
		Lib.assertTrue(ppn >= 0 && ppn < numPhyPages);
		return ppn2vpn[ppn];
	}

	public static boolean  isPinned(int ppn) {
		Lib.assertTrue(ppn >= 0 && ppn < numPhyPages);
		return pinned[ppn];
	}


	public static void pinPage(int ppn) {
//		kernelmutex.acquire();
		Lib.assertTrue(ppn >= 0 && ppn < numPhyPages);
		pinned[ppn] = true;
		pinCounter++;
//		kernelmutex.release();

	}
	
	
	public static void unpinPage(int ppn) {

		Lib.assertTrue(ppn >= 0 && ppn < numPhyPages);
		pinned[ppn] = false;
		pinCounter--;

	}

	public static boolean isAllPinned(){
		return pinCounter == numPhyPages;
	}
	public static int getFreeSwapPages() {
		if(freeSwapPages.size() == 0)
			return swapSize++;
		else
			return freeSwapPages.poll();
	}

	public static void returnFreeSwapPages(int spn) {
		Lib.assertTrue(spn < swapSize);
		freeSwapPages.offer(spn);
	}



	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	private static VMProcess[] ppn2Process;

	private static int[] ppn2vpn;

	private static boolean[] pinned;

	private static Queue<Integer> freeSwapPages = new LinkedList<>();

	private static int swapSize = 0;

	public static final String swpName = "_kernel.swp";

	public static OpenFile swpFile = null;

	private static int pinCounter = 0;
	
//	private static Lock kernelmutex = new Lock();


}

