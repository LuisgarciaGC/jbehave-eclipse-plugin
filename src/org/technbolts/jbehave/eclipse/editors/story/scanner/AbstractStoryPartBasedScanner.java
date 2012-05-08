package org.technbolts.jbehave.eclipse.editors.story.scanner;

import static org.technbolts.util.Objects.o;

import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.StringTokenizer;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.technbolts.eclipse.util.TextAttributeProvider;
import org.technbolts.jbehave.eclipse.JBehaveProject;
import org.technbolts.jbehave.eclipse.LocalizedStepSupport;
import org.technbolts.jbehave.eclipse.textstyle.TextStyle;
import org.technbolts.jbehave.eclipse.util.StoryPartDocumentUtils;
import org.technbolts.jbehave.parser.Constants;
import org.technbolts.jbehave.parser.Constants.TokenizerCallback;
import org.technbolts.jbehave.parser.ContentWithIgnorableEmitter;
import org.technbolts.jbehave.parser.ContentWithIgnorableEmitter.Callback;
import org.technbolts.jbehave.parser.StoryPart;
import org.technbolts.jbehave.parser.StoryPartVisitor;
import org.technbolts.util.New;

/**
 * 
 * 
 * {@link ITokenScanner} javadoc: <br/>
 * 
 * <p>
 * A token scanner scans a range of a document and reports about the token it finds. 
 * <b>A scanner has state</b>. When asked, the scanner returns the offset and the length 
 * of the last found token.
 * </p>
 */
public abstract class AbstractStoryPartBasedScanner implements ITokenScanner {
    
    private Logger log = LoggerFactory.getLogger(AbstractStoryPartBasedScanner.class);
    
    private final TextAttributeProvider textAttributeProvider;
    protected final JBehaveProject jbehaveProject;
    //
    private IToken defaultToken;
    private Token commentToken;
    protected Token exampleTableSepToken;
    protected Token exampleTableCellToken;
    //
    private List<Fragment> fragments;
    private int cursor = 0;
    //
    private IDocument document;
    private Region range;

    public AbstractStoryPartBasedScanner(JBehaveProject jbehaveProject, TextAttributeProvider textAttributeProvider) {
        this.jbehaveProject = jbehaveProject;
        this.textAttributeProvider = textAttributeProvider;
        textAttributeProvider.addObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                initialize();
            }
        });

    }
    
    /**
     * Initialize the scanner. This method is also called whenever {@link TextAttribute} get modified.
     */
    protected void initialize() {
        commentToken = newToken(TextStyle.COMMENT);
    }
    
    /**
     * Create a new token whose data is the {@link TextAttribute} matching the given styleId.
     * @param styleId
     * @return
     */
    protected Token newToken(String styleId) {
        TextAttribute textAttribute = textAttributeProvider.get(styleId);
        return new Token(textAttribute);
    }
    
    public void setDefaultToken(IToken defaultToken) {
        if(defaultToken==null)
            throw new IllegalArgumentException();
        this.defaultToken = defaultToken;
    }
    
    public IToken getDefaultToken() {
        return defaultToken;
    }
    
    /*
     * Returns the length of the last token read by this scanner.
     * 
     * @see org.eclipse.jface.text.rules.ITokenScanner#getTokenLength()
     */
    @Override
    public int getTokenLength() {
        return fragments.get(cursor).getLength();
    }
    
    /*
     * Returns the offset of the last token read by this scanner.
     * 
     * @see org.eclipse.jface.text.rules.ITokenScanner#getTokenOffset()
     */
    @Override
    public int getTokenOffset() {
        return fragments.get(cursor).getOffset();
    }
    
    /*
     * Returns the next token in the document.
     * 
     * @see org.eclipse.jface.text.rules.ITokenScanner#nextToken()
     */
    @Override
    public IToken nextToken() {
        if(cursor==-1) {
            evaluateFragments();
        }
        cursor++;
        if(cursor<fragments.size())
            return fragments.get(cursor).getToken();
        return Token.EOF;
    }
    
    protected void evaluateFragments() {
        StoryPartVisitor visitor = new StoryPartVisitor() {
            @Override
            public void visit(StoryPart part) {
                if(part.intersects(range.getOffset(), range.getLength()) && isPartAccepted(part))
                    emitPart(part); //part are given in the absolute position
            }
        };
        new StoryPartDocumentUtils(getLocalizedStepSupport()).traverseStoryParts(document, visitor);
    }

    protected LocalizedStepSupport getLocalizedStepSupport() {
        return jbehaveProject.getLocalizedStepSupport();
    }
    
    protected abstract boolean isPartAccepted(StoryPart part);

    protected static String f(String string) {
        return string.replace("\n", "\\n");
    }
    
    protected abstract void emitPart(StoryPart part);
    
    protected void emit(ContentWithIgnorableEmitter emitter, IToken token, int offset, int length) {
        emitter.emitNext(offset, length, emitterCallback(), token);
    }
    
    private Callback<IToken> emitterCallback;
    private Callback<IToken> emitterCallback() {
        if(emitterCallback==null) {
            emitterCallback = new Callback<IToken>() {
                @Override
                public void emit(IToken arg, int offset, int length) {
                    AbstractStoryPartBasedScanner.this.emit(arg, offset, length);
                }
                @Override
                public void emitIgnorable(int offset, int length) {
                    emit(commentToken, offset, length);
                }
            };
        }
        return emitterCallback;
    }

    protected void emit(IToken token, int offset, int length) {
        log.debug("Emitting ({}, offset: {}, length: {})",
                  o(token.getData(), offset, length));
        if(length==0) {
            log.debug("Empty token emitted zero length, data: {},  offset: {}, length: {}", o(token.getData(), offset, length));
        }
        else if(length<0) {
            log.error("Invalid token emitted negative length, data: {},  offset: {}, length: {}", o(token.getData(), offset, length));
        }
        else {
            log.debug("Token emitted, data: {},  offset: {}, length: {}, content: <{}>", o(token.getData(), offset, length, getContentForLog(offset, length)));
        }
        
        // can we merge previous one?
        if(!fragments.isEmpty()) {
            Fragment previous = getLastFragment();
            
            // check no hole
            int requiredOffset = previous.offset+previous.length;
            if(offset != requiredOffset) {
                log.debug("**hole completion**, offset: {} (vs required: {}), length: {}; previous offset: {}, length: {}",
                        o(offset, requiredOffset, length, previous.offset, previous.length));
                emit(getDefaultToken(), requiredOffset, offset-requiredOffset);
                previous = getLastFragment();
            }
            
            if(previous.token==token) {
                previous.length += length;
                log.debug("Token merged, offset: {}, length: {}", o(previous.offset, previous.length));
                return;
            }
        }
        Fragment fragment = new Fragment(token, offset, length);
        log.debug("Fragment added, offset: {}, length: {}", o(offset, length));
        fragments.add(fragment);
    }

    private String getContentForLog(int offset, int length) {
        return f(getContent(offset, length));
    }

    private String getContent(int offset, int length)  {
        try {
            return document.get(offset, length);
        } catch (BadLocationException e) {
            return "<<<n/a>>>";
        }
    }

    private Fragment getLastFragment() {
        return fragments.get(fragments.size()-1);
    }
    
    protected void emitTable(final ContentWithIgnorableEmitter emitter, final IToken defaultToken, final int offset, String content) {
        Constants.splitLine(content, new TokenizerCallback() {
            @Override
            public void token(int startOffset, int endOffset, String line, boolean isDelimiter) {
                if(isDelimiter)
                    emit(emitter, defaultToken, offset + startOffset, line.length());
                else if(line.trim().startsWith("|--"))
                    emit(emitter, commentToken, offset + startOffset, line.length());
                else
                    emitTableRow(emitter, defaultToken, offset + startOffset, line);
            }
        });
    }
    
    protected void emitCommentAware(final IToken defaultToken, final int offset, String content) {
        Constants.splitLine(content, new TokenizerCallback() {
            @Override
            public void token(int startOffset, int endOffset, String line, boolean isDelimiter) {
                if(line.trim().startsWith("!--"))
                    emit(commentToken, offset + startOffset, line.length());
                else
                    emit(defaultToken, offset + startOffset, line.length());
            }
        });
    }

    public Chain commentAwareChain(final IToken token) {
        return new Chain() {
            @Override
            public void next(int offset, String content) {
                emitCommentAware(token, offset, content);
            }
        };
    }
    
    protected void emitTableRow(ContentWithIgnorableEmitter emitter, IToken defaultToken, int offset, String line) {
        StringTokenizer tokenizer = new StringTokenizer(line, "|", true);
        int remaining = tokenizer.countTokens();
        boolean isFirst = true;
        while(tokenizer.hasMoreTokens()) {
            boolean isLast = (remaining==1);
            String tok = tokenizer.nextToken();
            int length = tok.length();
            
            if(tok.equals("|")) {
                emit(emitter, exampleTableSepToken, offset, length);
            }
            else if(isLast || isFirst) {
                emit(emitter, defaultToken, offset, length);
            }
            else {
                emit(emitter, exampleTableCellToken, offset, length);
            }
            
            offset += length;
            remaining--;
            isFirst = false;
        }
    }

    /**
     * Configures the scanner by providing access to the document range that should be scanned.
     * 
     * @see org.eclipse.jface.text.rules.ITokenScanner#setRange(org.eclipse.jface.text.IDocument, int, int)
     */
    @Override
    public void setRange(IDocument document, int offset, int length) {
        log.debug("Range(offset: " +  offset + ", length: " + length);

        fragments = New.arrayList();
        cursor = -1;
        this.document = document;
        this.range = new Region(offset, length);
    }
     
    public class Fragment {
        private IToken token;
        private int offset, length;
        private Fragment(IToken token, int offset, int length) {
            super();
            this.token = token;
            this.offset = offset;
            this.length = length;
        }
        @Override
        public String toString() {
            try {
                return token.getData() + ", offset: " + offset + ", length: " + length + ", c>>" + document.get(offset, length)+"<<";
            } catch (BadLocationException e) {
                return token.getData() + ", offset: " + offset + ", length: " + length + ", c>>" + "//BadLocationException//" +"<<";
            }
        }
        public int getOffset() {
            return offset;
        }
        public int getLength() {
            return length;
        }
        public IToken getToken() {
            return token;
        }
    }
    
    public interface Chain {
        void next(int offset, String content);
    }

}
