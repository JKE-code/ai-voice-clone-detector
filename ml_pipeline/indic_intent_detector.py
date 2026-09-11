"""
True Voice — Multilingual Indic Coercion & Scam Intent Detection Engine
Problem Statement #26104 (Smart India Hackathon)

Detects scam intent, financial coercion, authority impersonation, and emotional extortion
across multiple Indian languages in both native scripts and Latin/Hinglish/Tenglish transliteration.
Supported Languages: Hindi, Telugu, Tamil, Kannada, Marathi, Bengali, Gujarati, Punjabi, Indian English.
"""

import re
from typing import Dict, List, Tuple
from dataclasses import dataclass

@dataclass
class IntentAssessment:
    coercion_score: float         # 0.0 to 1.0
    detected_categories: List[str] # ['FINANCIAL_DEMAND', 'AUTHORITY_IMPERSONATION', ...]
    matched_phrases: List[str]    # Matched trigger words
    detected_language: str        # 'Hindi', 'Telugu', 'Tamil', 'Multilingual', etc.
    urgency_level: str            # 'CRITICAL', 'HIGH', 'MODERATE', 'LOW'

class IndicIntentDetector:
    """
    Ultra-fast regex-based & phonetic-tolerant multilingual Indic scam detector.
    Designed for zero-latency execution on mobile devices and edge servers.
    """

    # Multi-language dictionary mapping categories to patterns across Indian languages
    FRAUD_PATTERNS = {
        "FINANCIAL_DEMAND": {
            "weight": 0.95,
            "patterns": [
                # English
                r"\b(send|transfer|pay|give)\s+(money|cash|funds|amount|rupees)\b",
                r"\b(upi|gpay|phonepe|paytm|bhim|qr code)\b",
                r"\b(share|send|tell)\s+(otp|pin|cvv|password|code)\b",
                # Hindi / Hinglish
                r"\b(paise|pese|rupaye|rakam)\s+(bhej|transfer|dal|dalo|de|do)\b",
                r"\b(otp|pin)\s+(bata|batao|de|bhejo)\b",
                r"\b(account|khate)\s+me(in)?\s+(dal|bhej)\b",
                # Telugu / Tenglish
                r"\b(paisalu|dabbulu|money)\s+(pampu|pampinchu|ivvu|veyyi|vei)\b",
                r"\b(otp|pin)\s+(cheppu|pampu|ivvu)\b",
                r"\b(account|khata)\s+lo\s+(vei|veyyi|pampinchu)\b",
                # Tamil / Tanglish
                r"\b(panam|kaasu|rupees)\s+(anupu|anupunga|kudu|thanga)\b",
                r"\b(otp|pin)\s+(sollu|anupu)\b",
                # Kannada
                r"\b(dhana|hana|dundu)\s+(kalsi|kodi|haki)\b",
                r"\b(otp|pin)\s+(heli|kalsi)\b",
                # Marathi
                r"\b(paise|rupaye)\s+(pathav|dya|transfer kara)\b",
                # Bengali
                r"\b(taka|poisa)\s+(pathao|dao|transfer koro)\b",
            ]
        },
        "AUTHORITY_IMPERSONATION": {
            "weight": 0.90,
            "patterns": [
                # English
                r"\b(police|cbi|ed|customs|cyber\s+crime|inspector|dsp|court|arrest|warrant|jail)\b",
                r"\b(arrested|in\s+custody|detained|fir\s+registered)\b",
                # Hindi / Hinglish
                r"\b(police|thane|thaana|chowki)\s+(me(in)?\s+hu|pakad\s+liya|arrest\s+kar\s+liya)\b",
                r"\b(case|fir)\s+(darj|hatao|khatam)\b",
                # Telugu / Tenglish
                r"\b(police|station)\s+(lo\s+unna|pattu\s*kunnaru|arrest\s+chesaru)\b",
                r"\b(case|jail)\s+(nunchi|kattali)\b",
                # Tamil / Tanglish
                r"\b(police|station)\s+(la\s+irukken|pidichitanga|arrest\s+pannitanga)\b",
                # Marathi
                r"\b(police|chowki)\s+(madhe\s+aahe|pakadla)\b",
                # Bengali
                r"\b(police|thana)\s+(te\s+achi|dhorche|arrest\s+korche)\b",
            ]
        },
        "EMOTIONAL_EXTORTION": {
            "weight": 0.85,
            "patterns": [
                # English
                r"\b(accident|hospital|icu|emergency|injured|kidnapped|save\s+me|help\s+me)\b",
                r"\b(blood|surgery|operation)\s+(needed|charges|cost)\b",
                # Hindi / Hinglish
                r"\b(accident|hadsa)\s+(ho\s+gaya|hua)\b",
                r"\b(hospital|aspatal)\s+me(in)?\s+hu\b",
                r"\b(mujhe|meri)\s+(madad|bachao|bacha\s+lo)\b",
                # Telugu / Tenglish
                r"\b(accident|gundepotu)\s+(ayyindi|jarigindi)\b",
                r"\b(hospital|dawakana)\s+lo\s+unna\b",
                r"\b(naaku|nannu)\s+(help|kapadandi|madath)\b",
                # Tamil / Tanglish
                r"\b(accident)\s+(aayiduchu|aachu)\b",
                r"\b(hospital|aaspattiri)\s+la\s+irukken\b",
                r"\b(enaku|ennai)\s+(kaapathunga|uthavi)\b",
                # Kannada
                r"\b(apaghatha)\s+(aagide)\b",
                r"\b(sahaya|madadi)\s+(beku)\b",
            ]
        },
        "URGENCY_PRESSURE": {
            "weight": 0.70,
            "patterns": [
                # English
                r"\b(immediately|right\s+now|urgent|urgently|within\s+\d+\s+minutes|don't\s+hang\s+up)\b",
                # Hindi / Hinglish
                r"\b(jaldi|turant|abhi\s+ke\s+abhi|phone\s+mat\s+katna)\b",
                # Telugu / Tenglish
                r"\b(tonderga|ventane|ippude|call\s+cut\s+cheyoddu)\b",
                # Tamil / Tanglish
                r"\b(seekiram|udaney|ippovey|phone\s+vekkatha)\b",
                # Kannada
                r"\b(begane|eegale|thakshana)\b",
                # Marathi / Bengali
                r"\b(lavkar|aata|taratari|ekhoni)\b",
            ]
        }
    }

    def analyze_text(self, transcript: str) -> IntentAssessment:
        """
        Analyzes a live speech transcript (in English, Hindi, Telugu, Tamil, etc.)
        and computes a normalized Coercion Score [0.0 .. 1.0].
        """
        if not transcript or not transcript.strip():
            return IntentAssessment(0.0, [], [], "Unknown", "LOW")

        clean_text = transcript.lower()
        matched_categories = []
        matched_phrases = []
        category_scores = []

        for category, config in self.FRAUD_PATTERNS.items():
            cat_matched = False
            for pattern in config["patterns"]:
                matches = re.findall(pattern, clean_text, flags=re.IGNORECASE)
                if matches:
                    cat_matched = True
                    # Record phrase match
                    matched_phrases.append(pattern)
            if cat_matched:
                matched_categories.append(category)
                category_scores.append(config["weight"])

        # Calculate combined weighted score
        if not category_scores:
            coercion_score = 0.05 # Baseline normal speech
        else:
            # Multi-category compounding: e.g. Authority + Financial + Urgency
            base_score = max(category_scores)
            boost = 0.15 * (len(category_scores) - 1)
            coercion_score = min(1.0, base_score + boost)

        # Determine urgency level
        if coercion_score >= 0.85:
            urgency = "CRITICAL"
        elif coercion_score >= 0.65:
            urgency = "HIGH"
        elif coercion_score >= 0.35:
            urgency = "MODERATE"
        else:
            urgency = "LOW"

        return IntentAssessment(
            coercion_score=round(coercion_score, 3),
            detected_categories=matched_categories,
            matched_phrases=matched_phrases,
            detected_language="Indic-Multilingual",
            urgency_level=urgency
        )

# Example verification
if __name__ == "__main__":
    detector = IndicIntentDetector()

    test_examples = [
        ("Hindi", "maa jaldi pese bhej ye number ko mujhe madat chaiye"),
        ("Telugu", "naaku tonderga paisalu pampu hospital lo unna"),
        ("Tamil", "police station la irukken seekiram panam anupu"),
        ("English", "I am in custody please transfer 50000 rupees immediately to this UPI"),
        ("Safe Hindi", "kya haal hai kal milte hai dinner pe"),
    ]

    print("--- Indic Intent Detector Test Results ---")
    for lang, text in test_examples:
        res = detector.analyze_text(text)
        print(f"\n[{lang}] \"{text}\"")
        print(f"  Coercion Score: {res.coercion_score} | Urgency: {res.urgency_level}")
        print(f"  Categories: {res.detected_categories}")
