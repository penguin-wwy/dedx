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

--> Create class field writer

Append stack frame map and line number table

Optmization

### Test

```
gradle -q runTest
```


### Example

```
dedx -o /path/to/output /project_path/resource/Base.dex
```

Will create Base.class in `output` directory

And then, you can load this class and invoke method with the following code

```java
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ExampleLoader extends ClassLoader {
    public Class<?> defineClass(String name, byte[] bytes) {
        return defineClass(name, bytes, 0, bytes.length);
    }

    public static void main(String[] args) {
        ExampleLoader loader = new ExampleLoader();
        try {
            byte[] bytes = Files.readAllBytes(Paths.get("/path/to/Base.class"));
            Class baseClass = loader.defineClass("com.test.Base", bytes);
            Method addInt = baseClass.getMethod("addInt", int.class, int.class);
            assert (Integer) addInt.invoke(null, 1, 1) == 2;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```