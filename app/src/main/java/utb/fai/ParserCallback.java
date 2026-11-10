package utb.fai;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;

/**
 * Zpětné volání Callback pro parsování HTML dokumentů pomocí Swing ParserDelegatoru.
 * Vyhledává odkazy na další stránky a extrahuje text pro analýzu slov.
 */
class ParserCallback extends HTMLEditorKit.ParserCallback {

    URI pageURI;
    int depth = 0;
    int maxDepth = 5;
    HashSet<URI> visitedURIs;
    LinkedList<URIinfo> foundURIs;
    int debugLevel = 0;
    
    Map<String, Integer> wordCounts;

    public ParserCallback(HashSet<URI> visitedURIs, LinkedList<URIinfo> foundURIs) {
        this.visitedURIs = visitedURIs;
        this.foundURIs = foundURIs;
    }

    @Override
    public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos) {
        handleStartTag(t, a, pos);
    }

    @Override
    public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
        URI uri;
        String href = null;
        
        if (debugLevel > 1) {
            System.err.println("handleStartTag: " + t.toString() + ", pos=" + pos);
        }
        
        if (depth < maxDepth) {
            if (t == HTML.Tag.A) {
                href = (String) a.getAttribute(HTML.Attribute.HREF);
            } else if (t == HTML.Tag.FRAME) {
                href = (String) a.getAttribute(HTML.Attribute.SRC);
            }
        }
        
        if (href != null) {
            try {
                uri = pageURI.resolve(href);
                if (uri != null && uri.getScheme() != null && 
                    (uri.getScheme().equalsIgnoreCase("http") || uri.getScheme().equalsIgnoreCase("https"))) {
                    
                    if (!uri.isOpaque() && !visitedURIs.contains(uri)) {
                        visitedURIs.add(uri);
                        foundURIs.add(new URIinfo(uri, depth + 1));
                        
                        if (debugLevel > 0) {
                            System.err.println("Adding URI: " + uri.toString() + " at depth " + (depth + 1));
                        }
                    }
                }
            } catch (Exception e) {
                if (debugLevel > 0) {
                    System.err.println("Nalezeno nekorektní URI: " + href);
                }
            }
        }
    }

    @Override
    public void handleText(char[] data, int pos) {
        if (wordCounts == null) {
            return;
        }

        String text = new String(data);
        // Výraz pro extrakci slov (ignoruje čísla a speciální znaky)
        Pattern pattern = Pattern.compile("[\\p{L}]+");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String word = matcher.group().toLowerCase();
            // Počítáme jen slova delší než 1 znak
            if (word.length() > 1) {
                synchronized (wordCounts) {
                    wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
                }
            }
        }
    }
}