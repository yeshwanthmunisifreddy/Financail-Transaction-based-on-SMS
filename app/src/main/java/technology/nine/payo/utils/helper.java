package technology.nine.payo.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class helper {
    public static String getDateTimeString(Date date) {
        SimpleDateFormat localDateFormat = new SimpleDateFormat("dd MMM, YYYY hh:mm a");
        return localDateFormat.format(date);
    }

}
