# Nachos Kernel
## Objectives
### Part 1
Implement Kernel Thread in Nachos Kernel. 
- KThread.join
- Condition variable

Some utilities are also implemented
- Alarm
- Communicator, an application of Condition variable

### Part 2
Processes and Multiprogramming
- Handle file related system calls: creat, open, read, write, close, unlink
- Implemente virtual memory
- Handle process related system calls: exec, join, exit, halt

### Part 3
Virtual Memory
- Demand paging
- Lazy loading
- Page pinning

## Design
The complexity of designing such a system is that, the objectives often
correlate with each other in one methods, or multiple methods collobrate
together to realize one objective. It is better to implement objective by
objective, but keep logging the functionality of each methods.

> Nobody should start to undertake a large project. You start with a small trivial
project, and you should never expect it to get large. If you do, you'll just
overdesign and generally think it is more important than it likely is at that
stage. Or worse, you might be scared away by the sheer size of the work you
envision. So start small, and think about the details. Don't think about some
big picture and fancy design. If it doesn't solve some fairly immediate need,
it's almost certainly over-designed. And don't expect people to jump in and help
you. That's not how these things work. You need to get something half-way useful
first, and then others will say "hey, that almost works for me", and they'll get
involved in the project. -- Linus Torvalds

I thnk design starts from the data, while method are built around those data to realize the objective.

For data, the text follows a "contents, init, update, destroy" structure.
For methods, details are in the comment of codes.
### Kernel Space
KThread 
- `joined` is a static HashSet to remember the threads that have been joined. 
This can be  improved by use an boolean flag for each KThread Object.
This set is initialized when KThread is loaded by classLoader.
It adds value when joined; never deletes value. 
- `waitingForJoin` is a static HashMap, key being the thread to be joined
and value being the thread that joins: though not accurate,
the pair is like (child, parent), while parent joins at child. 
It adds value at `join` and removes value at `wakeJoin`.
- `join `
Waits for this thread to finish. It is called by the thread to be joined by the running 
thread: `child.join();` is called when parent is running. It should be an atomic
operation. Cannot join a thread twice. One thread cannot join itself.
Need to check whether it is finished, if so, return immediately.
- `wakeJoin` wake up the thread that is joinning the current thread. This 
method is called when `finishe()` is called. It requires the thread
to be finished.   

### User Space
UserKernel
- `protected static Queue<Integer> freePage = null;` Need to be protected by mutex. Initialzed 
use in Kernel initialization, by linking the number of free pages together. The maximum is numPhyPages.
	
UserProcess
- `private OpenFile[] fileTable;` 
A fileTable for each UserProcess, storing the files, with index being file descriptors.
Note that the first two descriptors are reserved for stdin and stdout.
Inited when UserProcess is constructed. Need to close all the files before exiting (in `cleanUp`)

- `protected static Lock mutex = new Lock();` A *static* lock for all UserProcess. It is 
used to protect the static fields in Kernel space. A Kernel space lock can also be used though.

- `private HashMap<Integer, UserProcess> children = new HashMap<>();` Store the children of
each process; use the pid of the child to find the Process Object; this is used when the parent is joining the children, as it is only allowed to
join its children. Children are added when they are successfully `execute`, not when they are 
created because they may not successfully executed. Elements are removed in `join` because each
process is only allowed to be joined by its parent once, different from Kernel space thread.join.

- `public static int processCounter = 0;` Counting the current number of userprocesses. It is increased
when a process is created; decrement when it has an execution failure or exits. Need to be used
when determine to terminate or not when exiting the last userprocess. Different from `idCounter`. Should be protected by mutex.

- `private static int idCounter = 0;` Process id counter; only increases; not considering integer overflow.
increments in the UserProcess constructor. Should be protected by mutex.

- `public boolean abnormalExit = false; private int exitStatus = -1;` The two combined is 
the exit status description of the process. `exitStatus` is set at `handleExit`. If `exitStatus`
not set, it may be `abnormalExit`.

- `private int pid = 0;` process id. Set in constructor. Should be protected by mutex.

VMKernel extends UserKernel
-	`private static VMProcess[] ppn2Process;`
    `private static int[] ppn2vpn;`
    `private static boolean[] pinned;`
This three are the invert table structure. It is the mapping from ppn to (vpn, Process, pinned). They are always set and unset with the pageTable entry.
- `static int pinCounter = 0;`
Pin counter is a quick way to record how many physical pages are pinned. This updates when pinned is set or unset.
- `private static Queue<Integer> freeSwapPages = new LinkedList<>();`
  `private static int swapSize = 0;`
  `public static final String swpName = "_kernel.swp";`
  `public static OpenFile swpFile = null;`
Swap file related structure. swapSize grows when ther eis no freeswappage. 

VMProcess extends UserProcess
- `private HashMap<Integer, Integer> swpTable = new HashMap<>();`
Swap table for each process, store a vpn to spn structure.
- `private static Condition waitForUnpinned = new Condition(mutex);`
A condition variable to store the process that have no physical pages to be allocated.

### Special design
- Maintain a UThread reference in UserProcess, which enables us to call
KThread.join to implement the join in user space
- To make handleExec works for VMProcess, we call `getNewProcess` method when
  initiate a new process. Then in VMProcess we need to override it to make use
  of polymorphism.



## Bugs
1. not setting dirty bit in `writeVirtualMemory`
2. overwrite regV0 after handling page fault
3. call writeVirtualMemory while holding the mutex (it is wrong because
handlePageFault may require that mutex)

## Acknowledgement
Special thanks to the instructor group of CSE120 2017Fall

