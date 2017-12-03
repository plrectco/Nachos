package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 * Assign pid to it, store it in the process pool
	 */
	public UserProcess() {


		fileTable = new OpenFile[maxFileNum];
		fileTable[0] = UserKernel.console.openForReading();
		fileTable[1] = UserKernel.console.openForWriting();

		mutex.acquire();
		pid = ++idCounter;
		allProcesses.put(pid, this);
		mutex.release();

	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
	        String name = Machine.getProcessClassName ();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals ("nachos.userprog.UserProcess")) {
		    return new UserProcess ();
		} else if (name.equals ("nachos.vm.VMProcess")) {
		    return new VMProcess ();
		} else {
		    return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		UThread ut = new UThread(this);
		ut.setName(name).fork();
		thisThread = ut;

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
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
			System.arraycopy(memory, translate(vaddr + read), data, offset+read, readLength);
			read += readLength;
		}


		return read;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
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
			System.arraycopy( data, offset+write, memory, translate(vaddr + write), writeLength);
			write += writeLength;
		}


		return write;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		mutex.acquire();
		if (numPages > UserKernel.getFreePageSize()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			mutex.release();
			return false;
		}

		pageTable = new TranslationEntry[numPages];
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			// coff
			for (int i = 0; i < section.getLength(); i++) {
				int ppn = UserKernel.getFreePage();
				int vpn = section.getFirstVPN() + i;
				section.loadPage(i, ppn);
				if(section.isReadOnly())
					pageTable[vpn] = new TranslationEntry(vpn, ppn, true, true, false, false);
				else
					pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
			}

		}

		//stack, and argument
		for(int vpn = numPages - stackPages - 1; vpn < numPages; vpn++) {
			int ppn = UserKernel.getFreePage();
			pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
		}

		mutex.release();
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		mutex.acquire();
		for(int i = 0; i < numPages; i++) {
			UserKernel.returnFreePage(pageTable[i].ppn);
		}
		mutex.release();

	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if(pid == 0)
			Machine.halt();
		else
			return -1;

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleExec(int nameAddr, int argc, int argvAddr) {
		String name = readVirtualMemoryString(nameAddr, 256);
		if(argc < 0) return -1;

		String[] argv = new String[argc];

		for(int i = 0; i < argc; i++) {
			byte [] addr = new byte[4];
			int r = readVirtualMemory(argvAddr + i*4, addr);
			if(r != 4) return -1;

			argv[i] = readVirtualMemoryString(Lib.bytesToInt(addr, 0), 256);

			if(argv[i] == null)
				return -1;
		}


		UserProcess child = new UserProcess();
		child.parent = this;
		if(!child.execute(name, argv))
		{
			mutex.acquire();
			allProcesses.remove(child.pid);
			mutex.release();
			return -1;
		}

		children.put(child.pid, child);

		return child.pid;
	}

	// can only join its children
	// each can be joined once
	// if had finished, then return
	private int handleJoin(int processID, int statusAddr) {
		if(!children.containsKey(processID)) return -1;
		UserProcess targetProcess = children.get(processID);
		// if it is none, then the target process has already return
		if(targetProcess == null)
			return 0;

		targetProcess.thisThread.join();

		children.remove(processID);
		if(targetProcess.abnormalExit)
			return 0;

		byte[] buffer = Lib.bytesFromInt(targetProcess.exitStatus);
		int r = writeVirtualMemory(statusAddr, buffer);
		if(r == 0) return -1;
		return 1;
	}

	private void cleanUp() {
		unloadSections();
		closeFiles();
		mutex.acquire();
		allProcesses.remove(pid);
		mutex.release();
		this.parent = null;
		if(allProcesses.isEmpty())
			Kernel.kernel.terminate();
		UThread.finish();
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
		Machine.autoGrader().finishingCurrentProcess(status);
		this.exitStatus = status;
		cleanUp();
		return 0;
	}

	// do we need to handle the case the file is already open in this process
	// no
	private int handleCreate(int nameAddr) {
		String name = readVirtualMemoryString(nameAddr, 256);
		if(name == null) return -1;

		OpenFile f = UserKernel.fileSystem.open(name, true);
		if(f == null) return  -1;

		int fd = findNextFd();

		if(fd != -1) {
			fileTable[fd] = f;
		}
		return fd;
	}

	private int handleOpen(int nameAddr) {
		String name = readVirtualMemoryString(nameAddr, 256);
		if(name == null) return -1;

		OpenFile f = UserKernel.fileSystem.open(name, false);
		if(f == null) return  -1;

		int fd = findNextFd();
		if(fd != -1) {
			fileTable[fd] = f;
		}
		return fd;
	}


	private int handleRead(int fd, int bufferAddr, int size) {
		// check fd, size
		if(fd < 0 || fd >= maxFileNum || size < 0) return -1;

		// check buffer
		if (bufferAddr < 0 || bufferAddr >= pageSize*numPages)
			return -1;

		OpenFile f = fileTable[fd];
		if(f == null) return -1;


		byte[] localBuffer = new byte[pageSize];
		int byteRead = 0;
		int i = 0, j = 0;

		// read page by page
		while(byteRead < size) {
			i = f.read(localBuffer, 0, Math.min(size - byteRead, pageSize));
			if(i <= 0) break; // reach the end of file, not error, but end of job
			j = writeVirtualMemory(bufferAddr + byteRead, localBuffer, 0, i);
			if(i != j) return -1;
			byteRead += i;
		}

		return byteRead;

	}

	//  buffer size
	private int handleWrite(int fd, int bufferAddr, int size) {
		// check fd, size
		if(fd < 0 || fd >= maxFileNum || size < 0) return -1;
		// check buffer

		if (bufferAddr < 0 || bufferAddr + size>= pageSize*numPages)
			return -1;

		OpenFile f = fileTable[fd];
		if(f == null) return -1;

		byte[] localBuffer = new byte[pageSize];
		int byteWrite = 0;
		int i = 0, j = 0;
		// read page by page
		while(byteWrite < size) {
			i = readVirtualMemory(bufferAddr + byteWrite, localBuffer, 0, Math.min(size - byteWrite, pageSize));
			if(i == 0) return -1;
			j = f.write(localBuffer, 0, i);
			if(j == -1 || i != j) return -1;
			byteWrite += i;
		}

		return byteWrite;

	}

	private int handleClose(int fd) {
		if(fd < 0 || fd >= maxFileNum) return -1;
		OpenFile f = fileTable[fd];
		if(f == null) return  -1;
		f.close();
		fileTable[fd] = null;
		return 0;
	}

	private int handleUnlink(int nameAddr) {
		String name = readVirtualMemoryString(nameAddr, 256);
		if(name == null) return -1;

		// if it exists in the current file table, close it.
		// don't need this part, as the exit will close the file
//		int fd = existedFile(name);
//		if(fd != -1) {
//			fileTable[fd].close();
//			fileTable[fd] = null;
//		}

		OpenFile f = UserKernel.fileSystem.open(name, false);
		if(f == null) return  -1; // file not exists

		// the file exists in filesystem
		f.close();
		UserKernel.fileSystem.remove(f.getName());

		return 0;
	}

	private final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				return handleExit(a0);
			case syscallCreate:
				return handleCreate(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0,a1, a2);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);


		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			abnormalExit = true;
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			cleanUp();
			// assert not reached means this should not be reached
			Lib.assertNotReached("Unexpected exception");
		}
	}



	/**
	 * Check if the address is read only
	 * Require address to be valid
	 * @param vddr virtual address
	 * @return physical address
	 */
	private boolean checkReadOnly(int vddr) {
		Lib.assertTrue(vddr >= 0 && vddr < pageSize*numPages);
		int vpn = vddr / pageSize;
		return pageTable[vpn].readOnly;
	}

	/**
	 * Translate virtual address to physical address
	 * Require address to be valid
	 * @param vddr virtual address
	 * @return physical address
	 */
	private int translate(int vddr) {
		Lib.assertTrue(vddr >= 0 && vddr < pageSize*numPages);
		int vpn = vddr / pageSize;
		int ppn = pageTable[vpn].ppn;
		return ppn*pageSize + vddr%pageSize;
	}

	/**
	 * Parse string from address
	 * @param addr
	 * @return parsed string
	 */
	private String getStringFromAddr(int addr) {
		byte[] buffer = new byte[256];
		int result = readVirtualMemory(addr, buffer,0, 256);
		int i = 0;
		for(; i < result; i++) {
			if(buffer[i] == '\0') break;
		}
		if(i == 256) return null;
		return new String(buffer, 0, i);
	}

	/**
	 * Check if the given file already open in the file table
	 * @param name
	 * @return -1 if not, otherwise return the fd
	 */
	private int existedFile(String name) {
		for(int i = 0; i < maxFileNum; i++) {
			if(fileTable[i] != null && fileTable[i].getName().equals(name))
				return i;
		}
		return -1;
	}

	/**
	 * Find the next available fd
	 * @return
	 */
	private int findNextFd() {
		for(int i = 0; i < maxFileNum; i++) {
			if(fileTable[i] == null)
				return i;
		}
		return -1;
	}

	private void closeFiles() {
		for(int i = 0; i < maxFileNum; i++) {
			if(fileTable[i] != null)
				fileTable[i].close();
		}
		if(coff != null)
			coff.close();
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	protected UThread thisThread = null;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private OpenFile[] fileTable;

	private final int maxFileNum = 16;

	private static Lock mutex = new Lock();

	private HashMap<Integer, UserProcess> children = new HashMap<>();

	private UserProcess parent = null;

	private static HashMap<Integer, UserProcess> allProcesses = new HashMap<>();

	private static int idCounter = 0;

	private int exitStatus = -1;

	private boolean abnormalExit = false;

	private int pid = 0;



}
