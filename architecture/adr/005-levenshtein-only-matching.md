# ADR-005: Levenshtein-Only Medicine Matching (No Brand/Generic Substitution)

## Status
Accepted

## Date
2026-02-05

## Context

Zaed Med Connect matches medicine donations with requests. The matching algorithm must determine when a donated medicine matches a requested medicine.

### The Core Question

When a user requests "Panadol" and someone donates "Paracetamol" (the generic equivalent), should we match them?

### Domain Knowledge (Egyptian Market)

After consultation with domain experts:

> "Egyptian patients want their EXACT medicine. If they ask for Panadol, they want Panadol - not the generic equivalent, even if it's chemically identical. Giving alternatives may result in rejection and loss of trust."

### Technical Options

1. **Levenshtein Distance Only**: Match typos/spelling variations of the SAME medicine name
2. **Brand/Generic Dictionary**: Map brand names to generic equivalents
3. **AI/ML Matching**: Use embeddings to understand medicine similarity

## Decision

We will use **Levenshtein distance matching ONLY** for typo correction. We will **NOT** implement brand-to-generic substitution.

### What Gets Matched

```
┌─────────────────────────────────────────────────────────────────┐
│                    MATCHING RULES                                │
│                                                                  │
│  ✅ ALLOWED MATCHES (Levenshtein ≤ 2):                          │
│  ───────────────────────────────────                            │
│  • "Panadol" → "Panadol"         (exact)                        │
│  • "Panadol" → "panadol"         (case)                         │
│  • "Panadol" → "Panadoll"        (typo: +1 char)                │
│  • "Panadol" → "Pnadol"          (typo: -1 char)                │
│  • "بانادول" → "باندول"          (Arabic typo: -1 char)         │
│  • "Brufen 400" → "Brufen 400mg" (format variation)             │
│                                                                  │
│  ❌ FORBIDDEN MATCHES:                                          │
│  ────────────────────                                           │
│  • "Panadol" → "Paracetamol"     (same drug, different brand)   │
│  • "Brufen" → "Ibuprofen"        (same drug, different brand)   │
│  • "Glucophage" → "Metformin"    (same drug, different brand)   │
│  • "بانادول" → "باراسيتامول"     (Arabic brand → generic)       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Algorithm Parameters

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Algorithm | Levenshtein Distance | Industry standard for typo detection |
| Max Distance | 2 characters | Catches common typos without false positives |
| Score Calculation | `100 - (distance / length * 100)` | Proportional to string length |
| Minimum Score | 60/100 | Below this, no match is made |

## Consequences

### Positive

1. **User Trust**: Patients get exactly what they requested
2. **No Rejection Risk**: Recipients won't refuse medicine due to unexpected substitution
3. **Safety**: Avoids any potential medical concerns about substitution
4. **Simplicity**: No need to maintain brand/generic dictionary
5. **Predictability**: Users understand why matches happen
6. **Cultural Fit**: Aligns with Egyptian patient preferences

### Negative

1. **Missed Opportunities**: "Panadol" donation won't match "Paracetamol" request even though they're equivalent
2. **Lower Match Rate**: Fewer automatic matches overall
3. **User Education Needed**: Donors should use exact names from packaging
4. **Duplicate Entries**: Same drug may appear as multiple "different" medicines

### Risks

| Risk | Mitigation |
|------|------------|
| Match rate too low | Monitor metrics; educate users to use exact package names |
| User frustration ("why didn't this match?") | Clear UI messaging explaining match criteria |
| Typos exceeding distance 2 | Provide medicine name autocomplete from database |

## Alternatives Considered

### Alternative 1: Brand/Generic Dictionary Matching

**Description**: Maintain a dictionary mapping brand names to generic equivalents. Match if either the direct name matches OR the generic equivalent matches.

**Example**:
```
Panadol → Paracetamol (generic)
Brufen → Ibuprofen (generic)

Request: "Paracetamol"
Donation: "Panadol"
Result: MATCH (via generic mapping)
```

**Pros**:
- Higher match rate
- More donations get distributed
- Medically sound (same active ingredient)

**Cons**:
- Egyptian patients may reject substitutes
- Must maintain extensive dictionary
- Dictionary maintenance burden (new drugs, brands)
- Potential trust issues

**Why Rejected**: Domain knowledge indicates Egyptian patients prefer exact medicines. The risk of rejection outweighs the benefit of higher match rates. **Trust > Optimization.**

### Alternative 2: AI/ML Embedding-Based Matching

**Description**: Use word embeddings or a trained model to understand medicine similarity.

**Pros**:
- Handles semantic similarity
- Could learn from historical matches
- Handles Arabic naturally with multilingual models

**Cons**:
- Black box (hard to explain why something matched)
- Requires training data
- Overkill for MVP
- Still has the trust problem (patients want exact medicines)

**Why Rejected**: Same trust issue as dictionary approach, plus unnecessary complexity for MVP.

### Alternative 3: Fuzzy Matching with Higher Threshold

**Description**: Use Levenshtein with a higher allowed distance (e.g., 5).

**Pros**:
- Catches more variations
- Still simple algorithm

**Cons**:
- Too many false positives
- "Brufen" would match "Bufferin" (distance 3) - completely different!
- Unsafe

**Why Rejected**: Higher distance leads to dangerous false positives. Distance 2 is the safe maximum.

## Implementation

### Levenshtein Implementation

```java
public class MedicineNameMatcher {

    private static final int MAX_LEVENSHTEIN_DISTANCE = 2;

    public MatchResult match(String requested, String donated) {
        // Normalize both names
        String normalizedRequested = normalize(requested);
        String normalizedDonated = normalize(donated);

        // Exact match (fastest path)
        if (normalizedRequested.equals(normalizedDonated)) {
            return MatchResult.exact();
        }

        // Levenshtein for typo tolerance
        int distance = levenshteinDistance(normalizedRequested, normalizedDonated);

        if (distance <= MAX_LEVENSHTEIN_DISTANCE) {
            int score = calculateScore(distance, normalizedRequested.length());
            return MatchResult.fuzzy(score);
        }

        return MatchResult.noMatch();
    }

    private String normalize(String name) {
        // See ADR-006 for Arabic normalization details
        return arabicNormalizer.normalize(name)
            .toLowerCase()
            .replaceAll("\\s*(mg|ملجم|مجم)\\s*", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
```

### Matching Score Formula

```
Overall Match Score = (NameSimilarity × 0.40)
                    + (ProximityScore × 0.30)
                    + (UrgencyBonus   × 0.20)
                    + (FreshnessScore × 0.10)

Where NameSimilarity = Levenshtein-based score (0-100)
      ProximityScore = Distance-based (same city = 100)
      UrgencyBonus   = HIGH=100, MEDIUM=50, LOW=20
      FreshnessScore = Days until expiry / 180 (capped at 100)

Minimum threshold to create match: 60
```

## Monitoring & Metrics

Track these metrics to validate the decision:

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Match rate | >30% of requests matched | <20% |
| False positive rate | <1% | >2% |
| User-reported wrong matches | 0 | Any |
| Average match score | >75 | <65 |

## References

- [Levenshtein Distance Algorithm](https://en.wikipedia.org/wiki/Levenshtein_distance)
- [Egyptian Pharmaceutical Market Report](https://www.mordorintelligence.com/industry-reports/egypt-pharmaceutical-market)
- Domain consultation notes (internal)
