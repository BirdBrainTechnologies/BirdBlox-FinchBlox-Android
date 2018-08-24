package com.birdbraintechnologies.birdblox.Util;


import android.content.Context;

import com.birdbraintechnologies.birdblox.R;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Steve on 6/9/2016.
 *
 * NamingHandler
 *
 * A class that handles naming devices.
 *
 */
public class NamingHandler {

    public static String GenerateName(Context context, String mac) {
        if (mac == null) return "";
        String result = "unknown";
        long mid;
        int i, j, k, offset, badWordsLen;
        String[] firstNames,middleNames,lastNames, badNames;
        firstNames = context.getResources().getStringArray(R.array.first_names);
        middleNames = context.getResources().getStringArray(R.array.middle_names);
        lastNames = context.getResources().getStringArray(R.array.last_names);
        badNames = context.getResources().getStringArray(R.array.bad_names);
        badWordsLen = badNames.length;
        List<String> badNamesList = Arrays.asList(badNames);
        // expected input: "aa:bb:cc:dd:ee:ff" => "d:ee:ff"
        mac = mac.substring(9);
        String first;
        String middle;
        String last;
        String prefix;

        // grab bits from the MAC address (6 bits, 6 bits, 8 bits => last, middle, first)
        i = Integer.parseInt(mac.substring(6),16);
        mid = Long.parseLong(mac.substring(1,2).concat(mac.substring(3,5)),16);
        k = (int)(mid / 64);
        j = (int)(mid % 64);

        // use the last 4 bits to "shift" all of the indices.
        offset = i % 16;
        i += offset;
        j += offset;
        k += offset;

        first = firstNames[i];
        middle = middleNames[j];
        last = lastNames[k];

        if (first != null && middle != null && last != null) {
            prefix = Character.toString(first.charAt(0)) + Character.toString(middle.charAt(0)) +
                     Character.toString(last.charAt(0));
            while (badNamesList.contains(prefix)) {
                j = (j + 1) % badWordsLen;
                middle = middleNames[j];
                prefix = Character.toString(first.charAt(0)) + Character.toString(middle.charAt(0)) +
                         Character.toString(last.charAt(0));
            }
            first = first.concat(" ");
            result = result.replace(result, first);
            middle = middle.concat(" ");
            result = result.concat(middle);
            result = result.concat(last);
        }
        return result;
    }

}
