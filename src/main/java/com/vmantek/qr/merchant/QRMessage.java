package com.vmantek.qr.merchant;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;

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
                f00 = "01";
            }
            if (length != 2)
            {
                throw new RuntimeException("Invalid data element 0");
            }

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
