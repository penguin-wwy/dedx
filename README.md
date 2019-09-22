# dedx

This is a tool to convert dex file to class file which similar to [dex2jar](https://github.com/pxb1988/dex2jar)

### Description

[dx](https://github.com/aosp-mirror/platform_dalvik/tree/master/dx) is the home of Dalvik eXchange, the thing that takes in class files and reformulates them for consumption in the dalvik VM.

Compared to dex2jar, with the help of dx.jar eliminate the trouble of instruction synchronization.

And through the high version of the asm package, can produce a higher version of jvm bytecode.

### Current progress

~~Parse Dex File~~

~~Parse Class~~

~~Parse Method~~

~~Parse Debug Info For Every Method~~

~~Instruction Generation~~

--> Optmization

### Test

```
gradle -q runTest
```
