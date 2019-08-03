package com.vmantek.qr.merchant;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * <p>
 * A parser/generator for QR Code based on the specification for Payment Systems (EMV QRCPS)
 * </p>
 * <p>
 * The QR Code is basically a TLV (Tag Length value) sequence
 * </p>
 * <p>
 * Each data object is made up of three individual fields.
 * The first field is an identifier (ID) by which the data object can be referenced.
 * The next field is a length field that explicitly indicates the number of characters
 * included in the third field: the value field.
 * A data object is then represented as an ID / Length / Value combination, where:
 * </p>
 * <ul>
 *     <li>The ID is coded as a two-digit numeric value, with a value ranging from "00" to "99"</li>
 *     <li>The length is coded as a two-digit numeric value, with a value ranging from "01" to "99</li>
 *     <li>The value field has a minimum length of one character and maximum length of 99 characters.</li>
 * </ul>
 * <p>
 *  In the QR Code, the data objects are organized in a tree-like structure, under the root.
 *  A data object may be a primitive data object or a template.
 *  A template  may include other templates and primitive data objects
 * </p>
 * <p>
 * QRMessage is a recursive tree, where a value could be a leaf (one of the supported primitive types) or
 * another QRMessage
 * </p>
 */
public class QRMessage
{
    private Map<String, Object> map = new LinkedHashMap<>();
    private boolean root = true;

    public QRMessage()
    {
    }

    private QRMessage(boolean root)
    {
        this.root = root;
    }

    public String pack()
    {
        String packed = _pack();
        if (packed.length() > 512)
        {
            throw new RuntimeException("QRMessage length exceeds 512 bytes");
        }
        return packed;
    }

    private String _pack()
    {
        StringBuilder sb = new StringBuilder();

        // Make sure element 0 is always present and first.
        if (root)
        {
            String f00 = (String) map.get("00");
            final int length = f00.length();
            if (f00 == null)
            {
                //QR Code Specification for Payment Systems
                //Payload Format Indicator
                //The Payload Format Indicator shall contain a value of "01".
                // All other values are RFU.
                f00 = "01";
            }
            if (length != 2)
            {
                throw new RuntimeException("Invalid data element 00");
            }
            //The Payload Format Indicator (ID "00") shall be the first data object in the QR Code.
            sb.append("00");
            sb.append(String.format("%02d", length));
            sb.append(f00);
        }

        for (Entry<String, Object> entry : map.entrySet())
        {
            final String tag = entry.getKey();
            int itag = Integer.valueOf(tag);
            if (itag < 0 || itag > 99)
            {
                throw new RuntimeException("Tag value must be between 00 and 99");
            }
            if ((itag == 0 || itag == 63) && root)
            {
                //Tag 00 and 63 must be included at start and end respectively
                continue;
            }

            Object _v = entry.getValue();
            String value = (_v instanceof QRMessage) ? ((QRMessage) _v).pack() : ((String) _v);
            sb.append(tag);
            sb.append(String.format("%02d", value.length()));
            sb.append(value);
        }

        // Ensure we produce the checksum
        if (root)
        {
            //The CRC (ID "63") shall be the last data object in the QR Code.
            String payload = sb.append("6304").toString();
            return payload + computeCRC(payload.getBytes(StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public void unpackTemplate(String tag)
    {
        final Object _v = map.get(tag);
        if (_v instanceof String)
        {
            QRMessage m = new QRMessage(false);
            m.unpack((String) _v);
            map.put(tag, m);
        }
    }

    public void unpack(String qrdata)
    {
        if (root)
        {
            validate(qrdata);
        }

        map.clear();
        int offset = 0;
        int end = qrdata.length();

        while (offset + 2 <= end)
        {
            String tag = qrdata.substring(offset, offset + 2);
            offset += 2;
            int len = Integer.parseInt(qrdata.substring(offset, offset + 2));
            offset += 2;
            String value = qrdata.substring(offset, offset + len);
            map.put(tag, value);
            offset += len;
        }

        if (root)
        {
            Set<String> tags = getTags();
            for (String tag : tags)
            {
                int t = Integer.parseInt(tag);
                if ((t >= 26 && t <= 51) || t == 62 || t == 64 || (t >= 80 && t <= 99))
                {
                    unpackTemplate(tag);
                }
            }
        }
    }

    public void unset(String fpath)
    {
        unsetPath(fpath);
    }

    public String get(String path)
    {
        Object o = getPath(path);
        return (o instanceof QRMessage) ? ((QRMessage) o).pack() : ((String) o);
    }

    public void set(String path, String value)
    {
        if (value == null)
        {
            unset(path);
        }
        else
        {
            setPath(path, value);
        }
    }

    private void unsetPath(String fpath)
    {
        StringTokenizer st = new StringTokenizer(fpath, ".");
        QRMessage m = this;
        QRMessage lastm = m;
        String tag = "";
        String lastTag;

        while (true)
        {
            lastTag = tag;
            tag = st.nextToken();
            if (st.hasMoreTokens())
            {
                Object obj = m.getValue(tag);
                if (obj instanceof QRMessage)
                {
                    lastm = m;
                    m = (QRMessage) obj;
                }
                else
                {
                    break;
                }
            }
            else
            {
                m.removeTag(tag);
                if (!m.hasFields() && !lastTag.equals(""))
                {
                    lastm.removeTag(lastTag);
                }
                break;
            }
        }
    }

    private void removeTag(String tag)
    {
        map.remove(tag);
    }

    private boolean hasFields()
    {
        return !map.isEmpty();
    }

    private Object getPath(String fpath)
    {
        QRMessage m = this;
        StringTokenizer st = new StringTokenizer(fpath, ".");
        Object o = null;

        while (true)
        {
            String tag = st.nextToken();
            o = m.getValue(tag);
            if (o == null)
            {
                break;
            }
            if (st.hasMoreTokens())
            {
                if (o instanceof QRMessage)
                {
                    m = (QRMessage) o;
                }
                else
                {
                    throw new RuntimeException("Invalid path");
                }
            }
            else
            {
                break;
            }
        }
        return o;
    }

    private void setPath(String fpath, Object value)
    {
        QRMessage m = this;
        StringTokenizer st = new StringTokenizer(fpath, ".");

        while (true)
        {
            String tag = st.nextToken();
            if (st.hasMoreTokens())
            {
                Object obj = m.getValue(tag);
                if (obj instanceof QRMessage)
                {
                    m = (QRMessage) obj;
                }
                else
                {
                    if (value == null)
                    {
                        break;
                    }
                    else
                    {
                        m.setPath(tag, m = new QRMessage(false));
                    }
                }
            }
            else
            {
                m.setValue(tag, value);
                break;
            }
        }
    }

    public Set<String> getTags()
    {
        return map.keySet();
    }

    public Set<String> getTags(String fpath)
    {
        Object o = getPath(fpath);
        if (o != null)
        {
            if (o instanceof QRMessage)
            {
                return ((QRMessage) o).getTags();
            }
        }
        return null;
    }

    private Object getValue(String tag)
    {
        return map.get(tag);
    }

    private void setValue(String tag, Object value)
    {
        map.put(tag, value);
    }

    private void validate(String s)
    {
        final boolean validFormat = s.startsWith("000201");
        final boolean crcValid = isCrcValid(s);

        if (!validFormat)
        {
            throw new RuntimeException("QR Message failed format validation (invalid header): " + s);
        }
        if (!crcValid)
        {
            throw new RuntimeException("QR Message failed CRC validation: " + s);
        }
    }

    private boolean isCrcValid(String s)
    {
        final String expectedCrc = s.substring(s.length() - 4);
        String crc = computeCRC(s.substring(0, s.length() - 4).getBytes(StandardCharsets.UTF_8));
        return expectedCrc.equals(crc);
    }

    /**
     * The checksum shall be calculated according to [ISO/IEC 13239] using the polynomial '1021' (hex)
     * and initial value 'FFFF' (hex).
     * The data over which the checksum is calculated shall cover all data objects, including their ID,
     * Length and Value, to be included in the QR Code, in their respective order,
     * as well as the ID and Length of the CRC itself (but excluding its Value)
     * @param bytes
     * @return
     */
    private String computeCRC(byte[] bytes)
    {
        int initValue = 0xFFFF;
        int polynomial = 0x1021;

        for (byte b : bytes)
        {
            for (int i = 0; i < 8; i++)
            {
                boolean bit = ((b >> (7 - i) & 1) == 1);
                boolean c15 = ((initValue >> 15 & 1) == 1);
                initValue <<= 1;
                if (c15 ^ bit)
                {
                    initValue ^= polynomial;
                }
            }
        }
        return String.format("%04X", initValue & 0xffff);
    }
}
