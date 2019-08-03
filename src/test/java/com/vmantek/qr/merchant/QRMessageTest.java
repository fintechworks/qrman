package com.vmantek.qr.merchant;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QRMessageTest
{
    @Test
    @DisplayName("Unpack works")
    public void unpackWorks()
    {
        String s=
            "00020101021229300012D156000000000510A93FO3230Q31280012D156000000010308" +
            "12345678520441115802CN5914BEST TRANSPORT6007BEIJING64200002ZH0104最佳运" +
            "输0202北京540523.7253031565502016233030412340603***0708A60086670902ME91" +
            "320016A0112233449988770708123456786304A13A";

        QRMessage m = new QRMessage();
        m.unpack(s);

        assertNull(m.get("99"));
        assertEquals("1234",m.get("62.03"));
        assertEquals("D15600000000",m.get("29.00"));
        assertEquals("01",m.get("00"));
        assertEquals("12",m.get("01"));
        assertEquals("0012D156000000000510A93FO3230Q",m.get("29"));
        assertEquals("0012D15600000001030812345678",m.get("31"));
        assertEquals("4111",m.get("52"));
        assertEquals("CN",m.get("58"));
        assertEquals("BEST TRANSPORT",m.get("59"));
        assertEquals("BEIJING",m.get("60"));
        assertEquals("0002ZH0104最佳运输0202北京",m.get("64"));
        assertEquals("23.72",m.get("54"));
        assertEquals("156",m.get("53"));
        assertEquals("01",m.get("55"));
        assertEquals("030412340603***0708A60086670902ME",m.get("62"));
        assertEquals("0016A011223344998877070812345678",m.get("91"));
        assertEquals("A13A",m.get("63"));
    }

    @Test
    @DisplayName("Pack/Unpack the same")
    public void packAndUnpackSame()
    {
        String s=
            "00020101021229300012D156000000000510A93FO3230Q31280012D156000000010308" +
            "12345678520441115802CN5914BEST TRANSPORT6007BEIJING64200002ZH0104最佳运" +
            "输0202北京540523.7253031565502016233030412340603***0708A60086670902ME91" +
            "320016A0112233449988770708123456786304A13A";

        QRMessage m = new QRMessage();
        m.unpack(s);
        String t=m.pack();

        assertEquals(s,t);
    }

    @Test
    @DisplayName("Pack from scratch")
    public void packFromScratch()
    {
        QRMessage m = new QRMessage();
        m.set("00","01");
        String t=m.pack();
        assertEquals("0002016304AAE6",t);
    }

    @Test
    @DisplayName("Ensure CRC is last")
    public void ensureCrcIsLast()
    {
        QRMessage m = new QRMessage();
        m.set("00","01");
        m.set("91","0016A011223344998877070812345678");
        String t=m.pack();
        assertEquals("63044D32",t.substring(t.length()-8));
    }

    @Test
    @DisplayName("Ensure Unset works")
    public void unsetWorks()
    {
        QRMessage m = new QRMessage();
        m.set("00","01");
        m.set("58","CN");
        m.set("91","0016A011223344998877070812345678");
        m.unpackTemplate("91");
        m.unset("91.00");
        m.set("58",null);
        assertNull(m.get("91.00"));
        assertNull(m.get("58"));
        String t=m.pack();
        assertEquals("63040E4D",t.substring(t.length()-8));
    }

    @Test
    @DisplayName("Ensure message is valid")
    public void ensureValidMessage()
    {
        // CRC Validation
        assertThrows(RuntimeException.class,()->{
            QRMessage m = new QRMessage();
            m.unpack("00020191320016A01122334499887707081234567863044D33");
        });

        // Message doesn't have a 00 tag or is badly formatted.
        assertThrows(RuntimeException.class,()->{
            QRMessage m = new QRMessage();
            m.unpack("C0020191320016A01122334499887707081234567863044D32");
        });

        // Tag has to be from 00 to 99.
        assertThrows(RuntimeException.class,()->{
            QRMessage m = new QRMessage();
            m.unpack("000201DD320016A01122334499887707081234567863044D32");
        });
    }
}
