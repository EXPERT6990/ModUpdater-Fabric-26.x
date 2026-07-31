package neelesh.easy_install.util;

import java.net.HttpURLConnection;
import java.util.ArrayList;
public class HttpClientManager {
    public static ArrayList<HttpURLConnection> connection = new ArrayList<>();

    public static void add(HttpURLConnection conn){
        connection.add(conn);
    }

    public static void remove(HttpURLConnection conn){
        connection.remove(conn);
    }

    public static void closeAll(){
        for(HttpURLConnection i : connection){
            i.disconnect();
        }
    }
}
