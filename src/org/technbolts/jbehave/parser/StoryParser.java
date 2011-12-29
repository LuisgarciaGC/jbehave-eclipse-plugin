package org.technbolts.jbehave.parser;

import java.util.List;

import org.technbolts.jbehave.support.JBKeyword;
import org.technbolts.util.CharIterator;
import org.technbolts.util.CharIterators;
import org.technbolts.util.CharTree;

public class StoryParser {
    
    public StoryParser() {
        super();
    }
    
    public List<StoryPart> parse(CharSequence content) {
        StoryPartCollector collector = new StoryPartCollector();
        parse(content, collector);
        return collector.getParts();
    }
    
    public void parse(CharSequence content, StoryPartVisitor visitor) {
        parse(CharIterators.createFrom(content), 0, visitor);
    }

    public void parse(CharIterator it, StoryPartVisitor visitor) {
        parse(it, 0, visitor);
    }

    public void parse(CharIterator it, int baseOffset, StoryPartVisitor visitor) {
        CharTree<JBKeyword> kwTree = Constants.sharedKeywordCharTree();
        int offset = baseOffset;
        Line line = new Line();
        Block block = new Block();
        block.reset(offset);
        line.reset(offset);
        while(true) {
            int read = it.read();
            if(read==CharIterator.EOF)
                break;
            
            line.append((char)read);
            if(isNewlineCharacter(read)) {
                if(line.startsWithBreakingKeyword(kwTree)) {
                    block.emitTo(visitor);
                    block.reset(line.offset);
                }
                line.emitTo(block);
                // line is reset without any char, offset must be the next one
                line.reset(offset+1);
            }
            offset++;
        }
        
        // remaining
        if(line.startsWithBreakingKeyword(kwTree)) {
            block.emitTo(visitor);
            block.reset(line.offset);
        }
        line.emitTo(block);
        block.emitTo(visitor);
    }
    
    private class Block {
        private StringBuilder buffer = new StringBuilder();
        private int offset;
        public void reset(int offset) {
            this.offset = offset;
            this.buffer.setLength(0);
        }
        public void emitTo(StoryPartVisitor visitor) {
            if(buffer.length()>0) {
                String content = buffer.toString();
                visitor.visit(new StoryPart(offset, content));
            }
        }
    }
    
    private class Line {
        private StringBuilder buffer = new StringBuilder();
        private int offset;
        public void append(char c) {
            buffer.append(c);
        }
        public void reset(int offset) {
            this.offset = offset;
            this.buffer.setLength(0);
        }
        public boolean startsWithBreakingKeyword (CharTree<JBKeyword> kwTree) {
            JBKeyword kw = kwTree.lookup(buffer);
            if(kw==null)
                return false;
            switch(kw) {
                case ExamplesTableHeaderSeparator:
                case ExamplesTableValueSeparator:
                case ExamplesTableIgnorableSeparator:
                    return false;
            }
            return true;
        }
        public void emitTo(Block block) {
            block.buffer.append(buffer);
        }
    }

    private static boolean isNewlineCharacter(int read) {
        return read=='\r' || read=='\n';
    }
}
