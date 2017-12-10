package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int vmLength = numPages*pageSize;

		if (vaddr < 0 || vaddr >= vmLength)
			return 0;

		// if the length extends beyond the VM space, it is an error, but
		// we still copy the part that is valid.
		int amount = Math.min(length, vmLength - vaddr);
		int read = 0;
		int readLength = 0;

		// readLength will not be zero after first init
		// amount make sure vaddr+read < vmLength
		// offset enables us to copy segment by segment
		// in the vmspace, all pages are valid
		while(read < amount) {
			if(readLength == 0)
				readLength =  Math.min(pageSize - vaddr%pageSize, amount - read);
			else
				readLength = Math.min(pageSize, amount - read);

			TranslationEntry entry = pageTable[(vaddr+read)/pageSize];

			needToPin = true;
			if(!entry.valid)
				handlePageFault((vaddr+read));

			mutex.acquire();
			VMKernel.pinPage(entry.ppn);
			System.arraycopy(memory, translate(vaddr + read), data, offset+read, readLength);
			entry.used = true;
			VMKernel.unpinPage(entry.ppn);
			waitForUnpinned.wake();
			mutex.release();
			read += readLength;
			needToPin = false;
		}


		return read;
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * Need to set dirty bit, used bit
	 * Need to handle pagefault
	 * Need to pin page
	 *
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);



		byte[] memory = Machine.processor().getMemory();
		int vmLength = numPages*pageSize;

		if (vaddr < 0 || vaddr >= vmLength)
			return 0;

		// if the length extends beyond the VM space, it is an error, but
		// we still copy the part that is valid.
		int amount = Math.min(length, vmLength - vaddr);
		int write = 0;
		int writeLength = 0;

		while(write < amount) {
			if(writeLength == 0)
				writeLength =  Math.min(pageSize - vaddr%pageSize, amount - write);
			else
				writeLength = Math.min(pageSize, amount - write);

			if(checkReadOnly(vaddr+write))
				return write;
			// copy from data to memory
			TranslationEntry entry = pageTable[(vaddr+write)/pageSize];
			needToPin = true;
			if(!entry.valid)
				handlePageFault((vaddr+write));

			// pinpage should be atomic
			mutex.acquire();
			VMKernel.pinPage(entry.ppn);

			System.arraycopy( data, offset+write, memory, translate(vaddr + write), writeLength);
			entry.used = true;
			entry.dirty = true;
			VMKernel.unpinPage(entry.ppn);

			waitForUnpinned.wake();
			mutex.release();
			write += writeLength;
			needToPin = false;
		}


		return write;
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 *
	 * load section implement lazy loading
	 *  - do not check the size of available physical pages
	 *  - do not load physical pages
	 *  - all vpn are set to be invalid
	 *
	 * @return <tt>true</tt> if successful.
	 */
	public boolean loadSections() {
//		mutex.acquire();
//		if (numPages > UserKernel.getFreePageSize()) {
//			coff.close();
//			Lib.debug(dbgProcess, "\tinsufficient physical memory");
//			mutex.release();
//			return false;
//		}
//
//		int numSections = coff.getNumSections();
//		for(int i = 0; i < numSections; i++) {
//			numCoffPages += coff.getSection(i).getLength();
//		}
//
//		pageTable = new TranslationEntry[numPages];
//		// allocate physical page
//		for(int i = 0; i < numPages; i++)
//		{
//			int ppn = UserKernel.getFreePage();
//			pageTable[i] = new TranslationEntry(i, ppn, false, false, false, false);
//		}
//		mutex.release();
//		return true;


		numCoffPages = numPages - stackPages - 1;
		pageTable = new TranslationEntry[numPages];
		for(int i = 0; i < numPages; i++)
		{
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	public void unloadSections() {
		mutex.acquire();
		for(int i = 0; i < numPages; i++) {
			if(pageTable[i].valid){
				VMKernel.unpinPage(pageTable[i].ppn);
				VMKernel.returnFreePage(pageTable[i].ppn);
				VMKernel.removeInvertTableMap(pageTable[i].ppn);
			}

		}
		waitForUnpinned.wakeAll();

		for(Map.Entry<Integer, Integer> entry: swpTable.entrySet()) {
			VMKernel.returnFreeSwapPages(entry.getValue());
		}
		mutex.release();

	}

	/**
	 * Prepare pages on demand
	 * and set the pagetable entry
	 *
	 * First get a ppn
	 * if a page has been swapped, then read it to the memory again
	 * else initialize the page, either a coff section by loading it
	 * from coff, or stack and argument by setting them to 0
	 *
	 * if vaddr is not a valid address, return -1
	 * @param vaddr the faulting address
	 * @return 0 if success, -1 on error
	 */
	public int handlePageFault(int vaddr) {
		if(vaddr < 0 || vaddr > numPages * pageSize)
			return -1;
		int vpn = vaddr/pageSize;
		Lib.debug(dbgProcess, "handle page fault of " + vpn);

		int ppn = getPPNFromKernel();

		mutex.acquire();
		if(needToPin)
			VMKernel.pinPage(ppn);
		VMKernel.setInvertTable(ppn, this, vpn);
		mutex.release();

		pageTable[vpn].ppn = ppn;
		pageTable[vpn].valid = true;
		pageTable[vpn].used = true;

		if(swpTable.containsKey(vpn)) {
			// has been swapped
			if(!readSwap(vpn, ppn)) {
				Lib.assertNotReached("Error reading swap file");
			}

		}
		else {
			// not have been swapped
			// initialize the page

			if (vpn >= numCoffPages) {
				// stack and argument, filled with 0
				byte[] memory = Machine.processor().getMemory();

				int paddr = ppn * pageSize;

				Arrays.fill(memory, paddr, paddr + pageSize, (byte) 0);
			} else {
				// coff
				int curCoffPage = 0;
				for (int i = 0; i < coff.getNumSections(); i++) {
					CoffSection cs = coff.getSection(i);
					int l = cs.getLength();
					if (curCoffPage + l <= vpn) curCoffPage += l;
					else {
						cs.loadPage(vpn - curCoffPage, ppn);
						if (cs.isReadOnly())
							pageTable[vpn].readOnly = true;
						break;
					}
				}

			}
		}


		Lib.debug(dbgProcess, "Physical page " + ppn +
		" is assigned to " + vpn);
 		return 0;
	}


	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * Handle pageFault, do not advance PC
	 *
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionPageFault:
				int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
				int result = handlePageFault(vaddr);
				if(result == -1){
					super.handleException(Processor.exceptionAddressError);
				}

				// Unlike other exception, after handle page fault exception, we do not advance PC.
				// Therefore if you the result of handlePageFault to Processor.regV0 as other exception handler does,
				// you will possibly modify the register value that is still useful for the current instruction!
				// Don't do this.
//				processor.writeRegister(Processor.regV0, result);
				break;
		default:
			super.handleException(cause);
			break;
		}
	}

	// require mutex
	// find one of the valid page to be evicted
	public int findVictim() {
		// clock algorithm
		while(true) {
			pagePtr = (pagePtr+1)%numPhyPages;
			if(VMKernel.isAllPinned()) {
				waitForUnpinned.sleep();
			}

			int vpn = VMKernel.getvpn(pagePtr);
			if(!isVPNValid(vpn)) continue;
			VMProcess vp = VMKernel.getVMProcess(pagePtr);
			if(vp == null) continue;
			TranslationEntry entry = vp.pageTable[vpn];
			if(entry.valid) {
				// redundant check, safe though
				if(entry.used)
					entry.used = false;
				else {
					if(!VMKernel.isPinned(pagePtr))
						break;
				}
			}

		}

		return pagePtr;
	}

	// require mutex
	// require the ppn is unpinned
	// require the pagetable entry is valid
	public boolean evict(int ppn) {


		int victimVPN = VMKernel.getvpn(ppn);
		VMProcess vp = VMKernel.getVMProcess(ppn);
		Lib.assertTrue(vp != null && victimVPN != -1);
		TranslationEntry victim = vp.pageTable[victimVPN];
		if(victim.dirty){
			Lib.debug(dbgProcess, ppn + " is dirty");
			int spn = VMKernel.getFreeSwapPages();
			vp.swpTable.put(victimVPN, spn);
			byte[] memory = Machine.processor().getMemory();
			int written = VMKernel.swpFile.write(spn*pageSize, memory, ppn*pageSize, pageSize);
			Lib.assertTrue(written == pageSize);
			Lib.debug(dbgProcess, "write vpn " + victimVPN + " spn "+ spn);
		}
		Lib.debug(dbgProcess, "evicting " + ppn + " from " + victimVPN);
		VMKernel.removeInvertTableMap(ppn);

		victim.valid = false;
		victim.readOnly = false;
		victim.dirty = false;
		victim.used = false;
		victim.ppn = -1;

		return true;
	}

	public int getPPNFromKernel() {
		mutex.acquire();
		int ppn = -1;
		if(VMKernel.getFreePageSize() > 0) {
			ppn = VMKernel.getFreePage();
		}
		else {
			ppn = findVictim();
			evict(ppn);
		}
		mutex.release();
		return ppn;
	}

	public boolean readSwap(int vpn, int ppn) {

		Lib.assertTrue(isVPNValid(vpn));
		int spn = swpTable.get(vpn);
		swpTable.remove(vpn);
		byte[] memory = Machine.processor().getMemory();
		byte[] localBuffer = new byte[pageSize];
		mutex.acquire();

		int i = VMKernel.swpFile.read(spn*pageSize, localBuffer, 0, pageSize);
		VMKernel.returnFreeSwapPages(spn);
		mutex.release();

		Lib.assertTrue(i == pageSize);
		int j = writeVirtualMemory(vpn*pageSize, localBuffer, 0, pageSize);
		Lib.assertTrue(i == j);

		Lib.debug(dbgProcess, "read from spn " + spn);


		return true;
	}

	// helper function for handleExec
	// create a VMProcess instead of a UserProcess
	public UserProcess getNewProcess() {
		return new VMProcess();
	}

	public boolean isVPNValid(int vpn) {
		return vpn >= 0 && vpn < numPages;
	}


	private static Condition waitForUnpinned = new Condition(mutex);

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private int numCoffPages = 0;

	private HashMap<Integer, Integer> swpTable = new HashMap<>();

	private static int pagePtr = 0;

	private static int numPhyPages = Machine.processor().getNumPhysPages();

	private boolean needToPin = false;

}
