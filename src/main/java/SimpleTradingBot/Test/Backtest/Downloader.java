package SimpleTradingBot.Test.Backtest;


public class Downloader {


    public static void main( String... args)  {

    }


    public static void allWords( int len ) {
        char[] chars = "abcdefghijklmnopqrstuvwxyz".toCharArray();
        iterate(chars, len, new char[len], 0);
    }

    public static void iterate(char[] chars, int len, char[] build, int pos) {
        if (pos == len) {
            String word = new String(build);
            System.out.println( word );
            return;
        }

        for (int i = 0; i < chars.length; i++) {
            build[pos] = chars[i];
            iterate(chars, len, build, pos + 1);
        }
    }
}
