package utb.fai;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import javax.swing.text.html.parser.ParserDelegator;

/**
 * Hlavní třída WebCrawleru pro předmět Vývoj síťových aplikací.
 * Zajišťuje rekurzivní procházení webových stránek a analýzu četnosti slov.
 */
public class App {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Chybějící parametr: Startovní URL adresa.");
            return;
        }

        LinkedList<URIinfo> foundURIs = new LinkedList<>();
        HashSet<URI> visitedURIs = new HashSet<>();
        
        // Sdílená thread-safe mapa pro ukládání četnosti slov napříč vlákny/parsováním
        Map<String, Integer> wordCounts = Collections.synchronizedMap(new HashMap<>());

        URI uri;
        try {
            String startUrlStr = args[0];
            uri = new URI(startUrlStr);
            foundURIs.add(new URIinfo(uri, 0));
            visitedURIs.add(uri);

            // Výchozí hloubka prohledávání
            int maxDepth = 2; 
            if (args.length > 1) {
                try {
                    maxDepth = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.err.println("Neplatná hloubka, používá se výchozí hodnota: 2");
                }
            }

            // Úroveň ladicích výpisů (0 = vypnuto, 1+ = zapnuto)
            int debugLevel = 0;
            if (args.length > 2) {
                try {
                    debugLevel = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    // Ignorujeme neplatný debug level
                }
            }

            ParserCallback callBack = new ParserCallback(visitedURIs, foundURIs);
            callBack.maxDepth = maxDepth;
            callBack.debugLevel = debugLevel;
            callBack.wordCounts = wordCounts;

            ParserDelegator parser = new ParserDelegator();

            // Hlavní smyčka pro procházení fronty nalezených URI
            while (!foundURIs.isEmpty()) {
                URIinfo currentInfo = foundURIs.removeFirst();
                callBack.depth = currentInfo.depth;
                callBack.pageURI = uri = currentInfo.uri;
                
                if (debugLevel > 0) {
                    System.err.println("Analyzing " + uri + " at depth " + currentInfo.depth);
                }
                
                try {
                    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                    // Nastavení User-Agent hlavičky pro obejití blokování serverů
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) WebCrawler/1.0");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    
                    if (connection.getResponseCode() >= 400) {
                        if (debugLevel > 0) {
                            System.err.println("HTTP Error " + connection.getResponseCode() + " for " + uri);
                        }
                        continue;
                    }

                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                    parser.parse(reader, callBack, true);
                    reader.close();
                    
                } catch (FileNotFoundException e) {
                    if (debugLevel > 0) {
                        System.err.println("Stránka nenalezena (404): " + uri);
                    }
                } catch (Exception e) {
                    if (debugLevel > 0) {
                        System.err.println("Chyba při čtení stránky " + uri + ": " + e.getMessage());
                    }
                }
            }

            // Výsledek: seřazení a výpis 20 nejčastějších slov
            printTopWords(wordCounts);

        } catch (Exception e) {
            System.err.println("Došlo k neošetřené výjimce za běhu aplikace:");
            e.printStackTrace();
        }
    }

    /**
     * Zpracuje mapu četností slov, seřadí je sestupně podle počtu výskytů
     * a vypíše top 20 nejčastějších slov ve formátu slovo-počet.
     */
    @SuppressWarnings("unchecked")
    private static void printTopWords(Map<String, Integer> wordCounts) {
        ArrayList sortedWords = new ArrayList(wordCounts.entrySet());
        sortedWords.sort((o1, o2) -> {
            Map.Entry e1 = (Map.Entry) o1;
            Map.Entry e2 = (Map.Entry) o2;
            Integer v1 = (Integer) e1.getValue();
            Integer v2 = (Integer) e2.getValue();
            return v2.compareTo(v1);
        });

        int limit = Math.min(20, sortedWords.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry entry = (Map.Entry) sortedWords.get(i);
            System.out.println(entry.getKey() + ";" + entry.getValue());
        }
    }
}