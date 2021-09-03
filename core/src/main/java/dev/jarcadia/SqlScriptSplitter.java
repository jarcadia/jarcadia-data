package dev.jarcadia;

import java.util.Iterator;
import java.util.NoSuchElementException;

class SqlScriptSplitter implements Iterable<String> {

    private final String script;

    protected SqlScriptSplitter(String script) {
        this.script = script;
    }

    @Override
    public Iterator<String> iterator() {
        return new StatementIterator(script);
    }

    protected static class StatementIterator implements Iterator<String> {

        private final String script;
        private final Iterator<Token> tokenizer;
        private int start;
        private int end;

        public StatementIterator(String script) {
            this.script = script;
            this.tokenizer = new TokenMerger(script, new Tokenizer(script));
            advance();
        }

        @Override
        public boolean hasNext() {
            return end != -1;
        }

        @Override
        public String next() {
            String statement = script.substring(start, end).trim();
            start = end;
            advance();
            return statement;
        }

        private void advance() {
            end = -1;
            int depth = 0;
            while(tokenizer.hasNext()) {
                Token token = tokenizer.next();
                if ("BEGIN".equalsIgnoreCase(token.value()) ||
                        "LOOP".equalsIgnoreCase(token.value()) ||
                        "IF".equalsIgnoreCase(token.value())) {
                    depth++;
                } else if ("END IF".equalsIgnoreCase(token.value()) ||
                        "END LOOP".equalsIgnoreCase(token.value()) ||
                        "END".equalsIgnoreCase(token.value())) {
                    depth--;
                } else if (";".equals(token.value()) && depth == 0) {
                    end = token.end();
                    return;
                }
            }
        }
    }

    protected static class TokenMerger implements Iterator<Token> {

        private final String script;
        private final Iterator<Token> tokenizer;
        private Token peeked;

        public TokenMerger(String script, Iterator<Token> tokenizer) {
            this.script = script;
            this.tokenizer = tokenizer;
        }

        @Override
        public boolean hasNext() {
            return peeked != null || tokenizer.hasNext();
        }

        @Override
        public Token next() {

            Token next = nextHelper();
            if ("END".equalsIgnoreCase(next.value())) {
                if (tokenizer.hasNext()) {
                    Token following = tokenizer.next();

                    if ("LOOP".equalsIgnoreCase(following.value()) ||
                            "IF".equalsIgnoreCase(following.value())) {

                        int start = next.start();
                        int end = following.end();
                        Token merged = new Token(script.substring(start, end).trim(), start, end);
                        return merged;
                    } else {
                        // Peeked ahead but didn't use the token so save it for later
                        peeked = following;
                        return next;
                    }
                } else {
                    // No token available for peeking so just return next
                    return next;
                }
            } else {
                return next;
            }
        }

        private Token nextHelper() {

            // If Token was peeked but unused, clear and return it as the next Token
            if (peeked != null) {
                Token temp = peeked;
                peeked = null;
                return temp;
            } else {
                return tokenizer.next();
            }
        }

        private void advance() {

        }
    }

    protected static class Tokenizer implements Iterator<Token> {

        private final String script;
        private int start;
        private int end;

        public Tokenizer(String script) {
            this.script = script;
            advance();
        }

        @Override
        public boolean hasNext() {
            return start < script.length();
        }

        @Override
        public Token next() {
            Token next = new Token(script.substring(start, end).trim(), start, end);
            start = end;
            advance();
            return next;
        }

        private void advance() {
            if (start == script.length()) {
                return;
            } else {
                char c = script.charAt(start);
                if (isSqlSymbol(c)) {
                    end++;
                } else {
                    while (end < script.length() - 1 && isSqlNameCharacter(script.charAt(end))) {
                        end++;
                    }
                }
                while (end < script.length() && Character.isWhitespace(script.charAt(end))) {
                    end++;
                }
            }
        }

        private boolean isSqlSymbol(char c) {
            return !Character.isWhitespace(c) && !isSqlNameCharacter(c);
        }

        private boolean isSqlNameCharacter(char c) {
            return c == '_' || Character.isLetterOrDigit(c);
        }
    }

    private record Token(String value, int start, int end) {};
}
