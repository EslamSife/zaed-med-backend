# ADR-006: Full Arabic Language Support

## Status
Accepted

## Date
2026-02-05

## Context

Zaed Med Connect serves the Egyptian market where:

- **Primary language**: Arabic (Egyptian dialect)
- **Medicine names**: Often in Arabic on packaging
- **User input**: Mix of Arabic and English
- **SMS messages**: Must support Arabic (80% of messages)

### Arabic Text Challenges

Arabic has unique characteristics that complicate text processing:

1. **Diacritics (Tashkeel)**: Optional marks above/below letters (فَتحة، ضَمة)
2. **Letter variants**: Same letter has different Unicode representations
3. **Right-to-left**: Display and input considerations
4. **Unicode encoding**: Affects SMS segment size (70 vs 160 chars)

### Examples of Arabic Variations

```
Same word, different representations:

أحمد  vs  احمد   (with/without Hamza on Alef)
بَنَادُول  vs  بنادول  (with/without diacritics)
ى  vs  ي         (Alef Maksura vs Yaa)
ة  vs  ه         (Taa Marbuta vs Haa)
```

## Decision

We will implement **full Arabic language support** across the platform, including:

1. **Text normalization** for medicine matching
2. **Bilingual database** (Arabic + English names)
3. **Arabic SMS templates** optimized for 70-character limit
4. **UTF-8 encoding** throughout the stack

### Arabic Text Normalization Rules

| Transformation | Before | After | Rationale |
|----------------|--------|-------|-----------|
| Remove diacritics | بَانَادُول | بانادول | Users rarely type diacritics |
| Normalize Alef | أ إ آ ا | ا | All Alef variants → plain Alef |
| Normalize Yaa | ى | ي | Alef Maksura = Yaa for matching |
| Normalize Taa Marbuta | ة | ه | End-of-word equivalence |
| Normalize Waw | ؤ | و | Waw with Hamza → plain Waw |
| Convert numerals | ٥٠٠ | 500 | Arabic/Western numeral equivalence |

### Implementation Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    ARABIC SUPPORT ARCHITECTURE                   │
│                                                                  │
│   User Input                                                    │
│   "بَانَادُول ٥٠٠ مِلجِرَام"                                     │
│        │                                                         │
│        ▼                                                         │
│   ┌─────────────────────────────────────────┐                   │
│   │       ARABIC TEXT NORMALIZER            │                   │
│   │                                          │                   │
│   │  1. Remove diacritics                   │                   │
│   │  2. Normalize letter variants           │                   │
│   │  3. Convert Arabic numerals             │                   │
│   │  4. Normalize whitespace                │                   │
│   └─────────────────────────────────────────┘                   │
│        │                                                         │
│        ▼                                                         │
│   Normalized: "بانادول 500 ملجرام"                               │
│        │                                                         │
│        ├──────────────────┬──────────────────┐                  │
│        ▼                  ▼                  ▼                  │
│   ┌─────────┐       ┌─────────┐       ┌─────────┐              │
│   │ Database│       │ Matching│       │   SMS   │              │
│   │ Storage │       │ Engine  │       │ Gateway │              │
│   └─────────┘       └─────────┘       └─────────┘              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Consequences

### Positive

1. **Native User Experience**: Egyptians can use Arabic naturally
2. **Better Matching**: Arabic medicine names match correctly despite variations
3. **Inclusive**: Supports users who prefer Arabic over English
4. **Market Fit**: Essential for Egyptian market success
5. **Trust**: Arabic interface feels local, not foreign

### Negative

1. **SMS Cost Impact**: Arabic SMS = 70 chars (vs 160), effectively 2x cost
2. **Development Complexity**: Must handle RTL, normalization, bidirectional text
3. **Testing Overhead**: Need Arabic test data and Arabic-speaking QA
4. **Database Size**: Storing both Arabic and English increases storage
5. **Font/Rendering**: Must ensure proper Arabic font support in all UIs

### Risks

| Risk | Mitigation |
|------|------------|
| SMS costs double for Arabic | Keep all templates under 70 chars; see ADR-004 |
| Normalization misses edge cases | Comprehensive test suite with real Egyptian text samples |
| Database encoding issues | Enforce UTF8MB4 everywhere; validate on insert |
| Search performance with Arabic | Proper indexes; consider PostgreSQL Arabic text search |

## Database Schema

```sql
-- Medicine table with bilingual support
CREATE TABLE medicines (
    id UUID PRIMARY KEY,

    -- English (required)
    name_en VARCHAR(255) NOT NULL,
    name_en_normalized VARCHAR(255) NOT NULL,

    -- Arabic (required for Egyptian market)
    name_ar VARCHAR(255) NOT NULL,
    name_ar_normalized VARCHAR(255) NOT NULL,

    -- Common typos and variations (both languages)
    aliases JSONB DEFAULT '[]',
    -- Example: ["باندول", "بندول", "Pnadol", "Panadoll"]

    -- Generic name (for reference, NOT for matching per ADR-005)
    generic_name_en VARCHAR(255),
    generic_name_ar VARCHAR(255),

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Ensure proper encoding
ALTER DATABASE zaed SET client_encoding TO 'UTF8';

-- Indexes for Arabic search
CREATE INDEX idx_medicines_ar_normalized ON medicines(name_ar_normalized);
CREATE INDEX idx_medicines_ar_trgm ON medicines USING GIN(name_ar gin_trgm_ops);
```

## Implementation

### Arabic Normalizer (Java)

```java
@Component
public class ArabicTextNormalizer {

    // Diacritics to remove
    private static final String DIACRITICS = "\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0653\u0654\u0655";

    // Character mappings
    private static final Map<Character, Character> ALEF_VARIANTS = Map.of(
        'أ', 'ا', 'إ', 'ا', 'آ', 'ا', 'ٱ', 'ا'
    );

    private static final Map<Character, Character> YAA_VARIANTS = Map.of(
        'ى', 'ي', 'ئ', 'ي'
    );

    private static final Map<Character, Character> ARABIC_NUMERALS = Map.of(
        '٠', '0', '١', '1', '٢', '2', '٣', '3', '٤', '4',
        '٥', '5', '٦', '6', '٧', '7', '٨', '8', '٩', '9'
    );

    public String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (char c : text.toCharArray()) {
            // Skip diacritics
            if (DIACRITICS.indexOf(c) >= 0) continue;

            // Apply normalizations
            char normalized = c;
            if (ALEF_VARIANTS.containsKey(c)) normalized = ALEF_VARIANTS.get(c);
            else if (YAA_VARIANTS.containsKey(c)) normalized = YAA_VARIANTS.get(c);
            else if (c == 'ة') normalized = 'ه';
            else if (c == 'ؤ') normalized = 'و';
            else if (ARABIC_NUMERALS.containsKey(c)) normalized = ARABIC_NUMERALS.get(c);

            result.append(normalized);
        }

        return result.toString()
            .toLowerCase()
            .replaceAll("\\s+", " ")
            .trim();
    }

    public boolean containsArabic(String text) {
        return text.chars().anyMatch(c -> c >= 0x0600 && c <= 0x06FF);
    }
}
```

### SMS Character Validator

```java
@Component
public class SmsCharacterValidator {

    private static final int UNICODE_SINGLE_LIMIT = 70;

    public SmsValidation validate(String message) {
        boolean hasArabic = containsArabic(message);
        int charLimit = hasArabic ? 70 : 160;
        int segments = (int) Math.ceil((double) message.length() / charLimit);

        return SmsValidation.builder()
            .characterCount(message.length())
            .isArabic(hasArabic)
            .segments(segments)
            .exceedsLimit(message.length() > charLimit)
            .warning(segments > 1 ?
                "Message will be sent as " + segments + " SMS segments" : null)
            .build();
    }
}
```

## Alternatives Considered

### Alternative 1: English-Only Platform

**Description**: Support only English, let users transliterate Arabic.

**Pros**:
- Simpler implementation
- Standard SMS costs
- No RTL handling needed

**Cons**:
- Poor user experience for Arabic speakers
- Medicine packages are often in Arabic
- Excludes less tech-savvy users

**Why Rejected**: Unacceptable for Egyptian market. Arabic support is a core requirement, not optional.

### Alternative 2: Transliteration (Franco-Arab)

**Description**: Accept "Arabizi" (Arabic in Latin letters), e.g., "banadol" for "بانادول".

**Pros**:
- Standard character encoding
- Familiar to young Egyptians
- SMS cost savings

**Cons**:
- Older users don't use Franco-Arab
- Medicine packages use Arabic script
- Ambiguous mapping (multiple ways to write same word)
- Not professional appearance

**Why Rejected**: Franco-Arab is informal and excludes older demographics. Professional platform should support proper Arabic.

### Alternative 3: AI-Based Normalization

**Description**: Use ML model for Arabic text normalization.

**Pros**:
- Handles edge cases better
- Could learn from user data

**Cons**:
- Overkill for this use case
- Adds infrastructure complexity
- Rule-based is sufficient for medicine names

**Why Rejected**: Rule-based normalization is sufficient and more predictable for medicine name matching.

## Testing Requirements

### Arabic Test Cases

```java
@Test
void shouldNormalizeDiacritics() {
    assertEquals("بانادول", normalizer.normalize("بَانَادُول"));
}

@Test
void shouldNormalizeAlefVariants() {
    assertEquals("اوجمنتين", normalizer.normalize("أوجمنتين"));
    assertEquals("اوجمنتين", normalizer.normalize("إوجمنتين"));
}

@Test
void shouldNormalizeArabicNumerals() {
    assertEquals("500", normalizer.normalize("٥٠٠"));
}

@Test
void shouldHandleMixedText() {
    assertEquals("بانادول 500 ملجرام", normalizer.normalize("بَانَادُول ٥٠٠ مِلجِرَام"));
}
```

## References

- [Unicode Arabic Block](https://en.wikipedia.org/wiki/Arabic_(Unicode_block))
- [Arabic Text Processing](https://www.w3.org/International/articles/arabic-forms/)
- [PostgreSQL Arabic Full-Text Search](https://www.postgresql.org/docs/current/textsearch.html)
