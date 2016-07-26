package com.esignal.signaldemo;

import java.util.Calendar;

/**
 * Created by tomcat on 2016/6/3.
 */
public class Utils
{
    private static final String TAG = Utils.class.getSimpleName();
    private static final String HEXES = "0123456789ABCDEF";

    public Utils()
    {}

    public static byte[] mlcTestCommand(byte fnCMDByte)
    {
        Calendar mCal = Calendar.getInstance();
        //final int   cmdLength=12;
        byte[]  cmdByte = {0x4D, (byte)0xFE, 0x00, 0x08, (byte)fnCMDByte
                ,(byte)(mCal.get(Calendar.YEAR)-2000)
                ,(byte)(mCal.get(Calendar.MONTH)+1)
                ,(byte)(mCal.get(Calendar.DATE))
                ,(byte)(mCal.get(Calendar.HOUR_OF_DAY))
                ,(byte)(mCal.get(Calendar.MINUTE))
                ,(byte)(mCal.get(Calendar.SECOND))
                ,0x00
        };

        //cmdByte[3] = (byte) cmdByte.length;
        byte sum = 0;
        for (int i=0; i<cmdByte.length-1; i++)
        {
            sum += cmdByte[i];
        }
        cmdByte[cmdByte.length-1] = sum;

        return cmdByte;
    }

    public static String getHexToString(byte[] raw)
    {
        if (raw == null)
        {
            return null;
        }
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (final byte b : raw)
        {
            hex.append(HEXES.charAt((b & 0xF0) >> 4))
                    .append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

}