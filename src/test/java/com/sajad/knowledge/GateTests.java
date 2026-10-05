package com.sajad.knowledge;
import com.sajad.knowledge.api.ApiException;
import com.sajad.knowledge.config.WorkGate;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import static org.junit.jupiter.api.Assertions.*;
class GateTests {
    @Test void timeoutKeepsBusyUntilWorkerExits() throws Exception {
        WorkGate gate=new WorkGate(1);var release=new CountDownLatch(1);
        try {
            assertEquals(504,assertThrows(ApiException.class,()->gate.run(d->{try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return "done";})).status());
            assertTrue(gate.busy());assertEquals(429,assertThrows(ApiException.class,()->gate.run(d->"no")).status());
            release.countDown();
            for(int i=0;i<100&&gate.busy();i++)Thread.sleep(10);
            assertFalse(gate.busy());assertEquals("ok",gate.run(d->"ok"));
        } finally {release.countDown();gate.close();}
    }
    @Test void failedWorkReleasesSlot() {
        WorkGate gate=new WorkGate(1);
        try {assertThrows(IllegalStateException.class,()->gate.run(d->{throw new IllegalStateException();}));assertFalse(gate.busy());}
        finally {gate.close();}
    }
}
