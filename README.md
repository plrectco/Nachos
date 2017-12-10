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
For fields, the text follows a "contents, init, update, destroy" structure.
For methods, the text follows a "contents, assumption/requirement, important implementation" structure.
### Kernel Space
KThread 
- `joined` is a static HashSet to remember the threads that have been joined. 
This can be  improved by use an boolean flag for each KThread Object.
This set is initialized when KThread is loaded by classLoader.
It adds value when joined; never deletes value. 
- `waitingForJoin` is a static HashMap, key being the thread to be joined
and value being the thread that joins: though not accurate,
the pair is like <child, parent>, while parent joins at child. 
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
Provide public methods
	
        public static int getFreePage() 
    
        public static int getFreePageSize() 
    
        public static void returnFreePage(int ppn) 

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

## Acknowledgement
Special thanks to the instructor group of CSE120 2017Fall

