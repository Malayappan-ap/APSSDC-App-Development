from PIL import Image
import pytesseract
import re

img = Image.open("test_portfolio.jpg")
text = pytesseract.image_to_string(img)

print("🔍 Raw OCR Text:")
print(text)

matches = re.findall(r'\b[A-Z]{2,10}\b', text.upper())
print("📈 Extracted Symbols:")
print(matches)
