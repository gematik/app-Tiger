package de.gematik.rbellogger.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RbelContentSliceTest {

  private RbelContent baseContent;
  private RbelContent contentSlice;

  @BeforeEach
  void setUp() {
    byte[] sampleData = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    baseContent = RbelContent.of(sampleData);
  }

  @Test
  void testTruncate() {
    contentSlice = baseContent.subArray(0, baseContent.size());
    contentSlice.truncate(5);
    assertEquals(5, contentSlice.size(), "Size should be updated after truncation");
    assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8), contentSlice.toByteArray());
  }

  @Test
  void testToByteArray() {
    contentSlice = baseContent.subArray(0, baseContent.size());
    byte[] expected = "Hello, World!".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(
        expected, contentSlice.toByteArray(), "Byte array should match the original content");
  }

  @Test
  void testGet() {
    contentSlice = baseContent.subArray(0, baseContent.size());
    assertEquals((byte) 'H', contentSlice.get(0), "First byte should be 'H'");
    assertEquals((byte) '!', contentSlice.get(12), "Last byte should be '!'");
  }

  @Test
  void testStartsWith() {
    contentSlice = baseContent.subArray(0, baseContent.size());
    byte[] prefix = "Hello".getBytes(StandardCharsets.UTF_8);
    assertTrue(contentSlice.startsWith(prefix), "Slice should start with 'Hello'");
  }

  @Test
  void testEndsWith() {
    contentSlice = baseContent.subArray(0, baseContent.size());
    byte[] postfix = "World!".getBytes(StandardCharsets.UTF_8);
    assertTrue(contentSlice.endsWith(postfix), "Slice should end with 'World!'");
  }

  @Test
  void testContains() {
    contentSlice = baseContent.subArray(0, baseContent.size());
    byte[] searchContent = "World".getBytes(StandardCharsets.UTF_8);
    assertTrue(contentSlice.contains(searchContent), "Slice should contain 'World'");
  }

  @Test
  void testIndexOf() {
    contentSlice = baseContent.subArray(0, baseContent.size());
    assertEquals(7, contentSlice.indexOf((byte) 'W'), "Index of 'W' should be 7");
    assertEquals(-1, contentSlice.indexOf((byte) 'X'), "Index of 'X' should be -1");
  }

  @Test
  void testSizeWithSubRange() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    assertEquals(5, contentSlice.size(), "Size should match the length of the subrange");
  }

  @Test
  void testTruncateWithSubRange() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    contentSlice.truncate(3); // "Wor"
    assertEquals(3, contentSlice.size(), "Size should be updated after truncation");
    assertArrayEquals("Wor".getBytes(StandardCharsets.UTF_8), contentSlice.toByteArray());
  }

  @Test
  void testToByteArrayWithSubRange() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    byte[] expected = "World".getBytes(StandardCharsets.UTF_8);
    assertArrayEquals(
        expected, contentSlice.toByteArray(), "Byte array should match the subrange content");
  }

  @Test
  void testGetWithSubRange() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    assertEquals((byte) 'W', contentSlice.get(0), "First byte should be 'W'");
    assertEquals((byte) 'd', contentSlice.get(4), "Last byte should be 'd'");
  }

  @Test
  void testStartsWithWithSubRange() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    byte[] prefix = "Wo".getBytes(StandardCharsets.UTF_8);
    assertTrue(contentSlice.startsWith(prefix), "Slice should start with 'Wo'");
  }

  @Test
  void testEndsWithWithSubRange() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    byte[] postfix = "ld".getBytes(StandardCharsets.UTF_8);
    assertTrue(contentSlice.endsWith(postfix), "Slice should end with 'ld'");
  }

  @Test
  void testContainsWithSubRange() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    byte[] searchContent = "orl".getBytes(StandardCharsets.UTF_8);
    assertTrue(contentSlice.contains(searchContent), "Slice should contain 'orl'");
  }

  @Test
  void testIndexOfWithSubRange() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    assertEquals(1, contentSlice.indexOf((byte) 'o'), "Index of 'o' should be 1");
    assertEquals(-1, contentSlice.indexOf((byte) 'H'), "Index of 'H' should be -1");
  }

  @Test
  void testNoCopyingForFullRange() {
    RbelContent fullRangeSlice = baseContent.subArray(0, baseContent.size());
    assertSame(
        baseContent, fullRangeSlice, "Full range slice should reference the original content");
  }

  @Test
  void testNoCopyingForFullRangeOfSubRange() {
    RbelContent slice = baseContent.subArray(2, 10);
    RbelContent slice2 = slice.subArray(0, slice.size());
    assertSame(slice, slice2, "Slice should reference the original content");
  }

  @Test
  void testStartsWithFailure() {
    contentSlice = baseContent.subArray(7, 12); // "World"
    byte[] prefix = "Hello".getBytes(StandardCharsets.UTF_8);
    assertFalse(contentSlice.startsWith(prefix), "Slice should not start with 'Hello'");
  }

  @Test
  void testEndsWithFailure() {
    contentSlice = baseContent.subArray(0, 5); // "Hello"
    byte[] postfix = "World!".getBytes(StandardCharsets.UTF_8);
    assertFalse(contentSlice.endsWith(postfix), "Slice should not end with 'World!'");
  }

  @Test
  void testContainsFailure() {
    contentSlice = baseContent.subArray(0, 5); // "Hello"
    byte[] searchContent = "World".getBytes(StandardCharsets.UTF_8);
    assertFalse(contentSlice.contains(searchContent), "Slice should not contain 'World'");
  }

  @Test
  void testIndexOfFailure() {
    contentSlice = baseContent.subArray(0, 5); // "Hello"
    assertEquals(-1, contentSlice.indexOf((byte) 'W'), "Index of 'W' should be -1 in the subrange");
  }
}
