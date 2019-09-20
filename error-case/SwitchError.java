// dx --dex --output=SwitchError.dex SwitchError.class
// dalvik occur error in packed-switch: target address is misplaced( -1 )

public class SwitchError {
    public int test(int a) {
        int res = 0;
        switch (a) {
            case 1: res = 1; break;
            case 2: res = 3; break;
            case 3: res = 6; break;
        }
        return res;
    }
}
