import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Since13Test {

    @Test
    public void versions() {
        assertFalse(Since13.newerThan13(""));
        assertFalse(Since13.newerThan13(null));
        assertFalse(Since13.newerThan13("JDK1.0"));
        assertFalse(Since13.newerThan13("1.2"));
        assertFalse(Since13.newerThan13("1.3"));
        assertTrue(Since13.newerThan13("1.4"));
        assertTrue(Since13.newerThan13("JDK1.4"));
        assertTrue(Since13.newerThan13("1.5"));
        assertTrue(Since13.newerThan13("5.0"));
        assertTrue(Since13.newerThan13("1.6"));
        assertTrue(Since13.newerThan13("8"));
    }

    @Test
    public void sourceAndBytecodeParametersMatch() {
        assertEquals("File,boolean", Since13.simpleParams("(Ljava/io/File;Z)V"));
        assertEquals("Object[],int[][],Entry", Since13.simpleParams("([Ljava/lang/Object;[[ILjava/util/Map$Entry;)V"));
        assertEquals("List", Since13.simpleType("java.util.List<? extends Map<K, V>>"));
        assertEquals("Object[]", Since13.simpleType("Object..."));
        assertEquals("byte[][]", Since13.simpleType("byte[][]"));
    }
}
