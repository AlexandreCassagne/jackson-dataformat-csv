package com.fasterxml.jackson.dataformat.csv;

import java.io.*;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Tests for verifying behavior of enclosing input stream as
 * a logical array.
 */
public class TestParserSequences extends ModuleTestBase
{
    @JsonPropertyOrder({"x", "y"})
    protected static class Entry {
        public int x, y;

        public Entry() { }
        public Entry(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public boolean equals(Entry e) { // simplified just for testing
            return e.x == this.x && e.y == this.y;
        }

        @Override
        public String toString() {
            return "["+x+","+y+"]";
        }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    // Test using non-wrapped sequence of entries
    public void testAsSequence() throws Exception
    {
        CsvMapper mapper = mapperForCsv();
        mapper.disable(CsvParser.Feature.WRAP_AS_ARRAY);
        MappingIterator<Entry> it = mapper.readerWithSchemaFor(Entry.class).readValues(
                "1,2\n-3,0\n5,6\n");
        Entry entry;
        
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(1, entry.x);
        assertEquals(2, entry.y);
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(-3, entry.x);
        assertEquals(0, entry.y);
        assertTrue(it.hasNext());
        assertNotNull(entry = it.next());
        assertEquals(5, entry.x);
        assertEquals(6, entry.y);
        assertFalse(it.hasNext());
    }

    // Test using sequence of entries wrapped in a logical array.
    public void testAsWrappedArray() throws Exception
    {
        CsvMapper mapper = mapperForCsv();
        mapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
        Entry[] entries = mapper.readerWithSchemaFor(Entry.class).withType(Entry[].class)
                .readValue("1,2\n0,0\n123,123456789\n");
        assertEquals(3, entries.length);
        assertEquals(1, entries[0].x);
        assertEquals(2, entries[0].y);
        assertEquals(0, entries[1].x);
        assertEquals(0, entries[1].y);
        assertEquals(123, entries[2].x);
        assertEquals(123456789, entries[2].y);
    }

    // Test for teasing out buffer-edge conditions...
    public void testLongerUnwrapped() throws Exception
    {
        // how many? about 10-20 bytes per entry, so try to get at ~100k -> about 10k entries
        List<Entry> entries = generateEntries(9999);
        CsvMapper mapper = mapperForCsv();
        CsvSchema schema = mapper.schemaFor(Entry.class);
        ObjectWriter writer = mapper.writer(schema);
        mapper.disable(CsvParser.Feature.WRAP_AS_ARRAY);

        // First, using bytes; note
        byte[] bytes = writer.writeValueAsBytes(entries);
        // ... we just happen to know this is expected length...
        final int EXPECTED_BYTES = 97640;
        assertEquals(EXPECTED_BYTES, bytes.length);

        MappingIterator<Entry> it = mapper.reader(Entry.class).with(schema).readValues(bytes, 0, bytes.length);
        verifySame(it, entries);
        bytes = null;
        
        // and then chars: NOTE: ASCII, so bytes == chars
        String text = writer.writeValueAsString(entries);
        assertEquals(EXPECTED_BYTES, text.length());

        it = mapper.reader(Entry.class).with(schema).readValues(text);
        verifySame(it, entries);
    
    }

    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    private List<Entry> generateEntries(int count) throws IOException
    {
        Random rnd = new Random(count);
        ArrayList<Entry> entries = new ArrayList<Entry>(count);
        while (--count >= 0) {
            entries.add(new Entry(rnd.nextInt() % 9876, 50 - (rnd.nextInt() % 987)));
        }
        return entries;
    }

    private void verifySame(Iterator<Entry> it, List<Entry> exp)
    {
        Iterator<Entry> expIt = exp.iterator();
        int count = 0;
        
        while (it.hasNext()) {
            if (!expIt.hasNext()) {
                fail("Expected "+exp.size()+" entries; got more");
            }
            Entry expEntry = expIt.next();
            Entry actualEntry = it.next();
            if (!expEntry.equals(actualEntry)) {
                fail("Entry at "+count+" (of "+exp.size()+") differs: exp = "+expEntry+", actual = "+actualEntry);
            }
            ++count;
        }
        
        if (expIt.hasNext()) {
            fail("Expected "+exp.size()+" entries; got only "+count);
        }
    }
}

