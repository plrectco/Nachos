/*
 * write4.c
 *
 * Echo lines of input to the output.  Terminate on a ".".  Requires basic
 * functionality for both write and read.
 *
 * Invoking as "java nachos.machine.Machine -x write4.coff" will echo
 * the characters you type at the prompt (using the "../bin/nachos"
 * script turns off echo).
 *
 * Geoff Voelker
 * 11/9/15
 */

#include "stdio.h"
#include "stdlib.h"

int
main (int argc, char *argv[])
{
    printf("Here we are\n");
    // read-abc-3: Test reading short file w/ length greater than file size
    int fd = open("write1.c");
    int buffer[1024];
    int r = read(fd, buffer, 102400);
    printf("Read %d\n", r);
    return 0;
}
