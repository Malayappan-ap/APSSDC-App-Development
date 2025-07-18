from flask import Flask, request, jsonify
from flask_cors import CORS, cross_origin
import torch
import torch.nn as nn
import yfinance as yf
import numpy as np
from sklearn.preprocessing import MinMaxScaler
from PIL import Image
import pytesseract
import re
import warnings

# OPTIONAL: Suppress warnings
warnings.simplefilter(action='ignore', category=FutureWarning)

# Tesseract path (if needed)
pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'

app = Flask(__name__)
CORS(app, supports_credentials=True)

# ==== Model Definition ====
SEQ_LEN = 60

class HybridModel(nn.Module):
    def __init__(self, input_size, hidden_size, num_layers=2):
        super(HybridModel, self).__init__()
        self.lstm = nn.LSTM(input_size, hidden_size, num_layers, batch_first=True)
        self.transformer = nn.TransformerEncoder(
            nn.TransformerEncoderLayer(d_model=hidden_size, nhead=4, batch_first=True), num_layers=1)
        self.fc = nn.Linear(hidden_size, 1)

    def forward(self, x):
        lstm_out, _ = self.lstm(x)
        transformer_out = self.transformer(lstm_out)
        out = self.fc(transformer_out[:, -1, :])
        return out

# ==== Load Trained Model ====
model = HybridModel(input_size=1, hidden_size=64)
model.load_state_dict(torch.load("hybrid_model.pth", map_location=torch.device('cpu')))
model.eval()

# ==== Helper to Load Stock Data ====
def load_stock_data_yfinance(symbol):
    df = yf.download(symbol, interval="5m", period="5d", progress=False)
    if df.empty:
        raise ValueError("Invalid or unsupported stock symbol.")
    df = df[['Close']].dropna()

    scaler = MinMaxScaler()
    scaled = scaler.fit_transform(df)

    X = [scaled[i-SEQ_LEN:i] for i in range(SEQ_LEN, len(scaled))]
    last_seq = torch.tensor(np.array(X)[-1:], dtype=torch.float32)

    current_price = float(df['Close'].iloc[-1])
    yesterday_price = float(df['Close'].iloc[-2])

    return last_seq, scaler, current_price, yesterday_price

# ==== /predict Endpoint ====
@app.route("/predict", methods=["POST", "OPTIONS"])
@cross_origin(origin='*', headers=['Content-Type', 'Authorization'])
def predict():
    if request.method == "OPTIONS":
        return '', 204

    data = request.get_json()
    symbol = data.get("symbol")
    if not symbol:
        return jsonify({"error": "No stock symbol provided"}), 400

    try:
        last_seq, scaler, current_price, yesterday_price = load_stock_data_yfinance(symbol)
        with torch.no_grad():
            pred = model(last_seq)
        predicted_price = scaler.inverse_transform(pred.numpy())[0][0]

        if predicted_price > current_price * 1.01:
            advice = "Buy"
        elif predicted_price < current_price * 0.99:
            advice = "Sell"
        else:
            advice = "Hold"

        return jsonify({
            "symbol": symbol.upper(),
            "current_price": round(current_price, 2),
            "yesterday_close": round(yesterday_price, 2),
            "predicted_price": round(predicted_price, 2),
            "advice": advice
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ==== /analyze-portfolio Endpoint ====
@app.route('/analyze-portfolio', methods=['POST'])
@cross_origin(origin='*')
def analyze_portfolio():
    if 'image' not in request.files:
        return jsonify({'error': 'No image uploaded'}), 400

    image_file = request.files['image']
    image = Image.open(image_file.stream)

    try:
        text = pytesseract.image_to_string(image)
        matches = re.findall(r'\b[A-Z]{3,10}\b', text.upper())
        blacklist = {"QTY", "AVG", "INVESTED", "LTP"}
        symbols = list(set([m for m in matches if m not in blacklist]))

        print("🧠 Extracted Symbols:", symbols)

        if not symbols:
            return jsonify({"error": "No stock symbols detected", "suggestions": []}), 404

        results = []
        for symbol in symbols:
            tried_symbols = [symbol + ".NS", symbol + ".BO", symbol]  # NSE → BSE → fallback
            data_loaded = False

            for sym in tried_symbols:
                try:
                    last_seq, scaler, current_price, yesterday_price = load_stock_data_yfinance(sym)
                    actual_symbol = sym
                    data_loaded = True
                    break
                except Exception as e:
                    print(f"⚠️ Failed loading {sym}: {e}")
                    continue

            if not data_loaded:
                print(f"❌ Skipping {symbol} - no valid suffix worked")
                continue

            with torch.no_grad():
                pred = model(last_seq)
            predicted_price = scaler.inverse_transform(pred.numpy())[0][0]

            if predicted_price > current_price * 1.01:
                advice = "Buy"
            elif predicted_price < current_price * 0.99:
                advice = "Sell"
            else:
                advice = "Hold"

            results.append({
                "symbol": actual_symbol,
                "current_price": round(float(current_price), 2),
                "yesterday_close": round(float(yesterday_price), 2),
                "predicted_price": round(float(predicted_price), 2),
                "advice": advice
            })

        return jsonify({"stocks": results})  # 🔥 fix key for Android app

    except Exception as e:
        print("🔥 Error in /analyze-portfolio:", str(e))
        return jsonify({"error": str(e)}), 500
# ==== /extract-symbols Endpoint ====
@app.route("/extract-symbols", methods=["POST"])
@cross_origin(origin='*')
def extract_symbols():
    if 'image' not in request.files:
        return jsonify({'error': 'No image uploaded'}), 400

    image_file = request.files['image']
    image = Image.open(image_file.stream)

    try:
        text = pytesseract.image_to_string(image)
        matches = re.findall(r'\b[A-Z]{3,}\b', text.upper())
        blacklist = {"QTY", "AVG", "INVESTED", "LTP"}
        valid_symbols = list(set([m for m in matches if m not in blacklist]))

        if not valid_symbols:
            return jsonify({
                "error": "No stock symbols detected",
                "suggestions": ["COCHINSHIP", "GOLDBEES", "GREENPOWER", "MAZDOCK", "SUZLON"]
            }), 404

        return jsonify({"detected_symbols": valid_symbols})

    except Exception as e:
        return jsonify({"error": str(e)}), 500
@app.route("/suggest-stocks", methods=["POST"])
@cross_origin(origin='*')
def suggest_stocks():
    try:
        data = request.get_json()
        budget = float(data.get("amount", 0))

        if budget <= 0:
            return jsonify({"error": "Invalid amount"}), 400

        # 🔥 Real beginner-friendly, popular stocks (expandable)
        stock_list = [
            "HDFCBANK.NS", "ICICIBANK.NS", "INFY.NS", "ITC.NS", "RELIANCE.NS", "TATAMOTORS.NS",
            "SBIN.NS", "AXISBANK.NS", "LT.NS", "COALINDIA.NS", "WIPRO.NS", "POWERGRID.NS",
            "ONGC.NS", "NTPC.NS", "BPCL.NS", "IOC.NS", "ADANIPORTS.NS", "TCS.NS", "MARUTI.NS",
            "TECHM.NS", "ULTRACEMCO.NS", "BAJAJFINSV.NS", "BAJFINANCE.NS", "HINDUNILVR.NS",
            "DIVISLAB.NS", "CIPLA.NS", "SUNPHARMA.NS", "DRREDDY.NS", "TITAN.NS", "BAJAJ-AUTO.NS",
            "HCLTECH.NS", "GAIL.NS", "GRASIM.NS", "JSWSTEEL.NS", "HINDALCO.NS", "TATASTEEL.NS",
            "UPL.NS", "M&M.NS", "EICHERMOT.NS", "SHREECEM.NS", "HEROMOTOCO.NS", "BRITANNIA.NS",
            "NESTLEIND.NS", "ASIANPAINT.NS", "SBILIFE.NS", "HDFCLIFE.NS", "BAJAJHLDNG.NS"
        ]

        affordable_stocks = []
        max_per_stock = budget / 5  # Only suggest stocks <= 20% of budget

        for symbol in stock_list:
            try:
                df = yf.download(symbol, period="1d", interval="1d", progress=False)
                if df.empty:
                    continue
                price = float(df['Close'].iloc[-1])
                if price <= max_per_stock:
                    affordable_stocks.append((symbol, price))
            except:
                continue

        if not affordable_stocks:
            return jsonify({"error": "No affordable stocks found"}), 404

        # ✅ Split evenly across all affordable stocks
        split_budget = budget / len(affordable_stocks)

        suggestions = []
        for symbol, price in affordable_stocks:
            quantity = int(split_budget // price)
            if quantity > 0:
                suggestions.append({
                    "symbol": symbol,
                    "current_price": round(price, 2),
                    "quantity": quantity
                })

        if not suggestions:
            return jsonify({"error": "Budget too low for suggested stocks"}), 404

        print("📩 Received budget:", budget)
        print("✅ Final Suggestions:", suggestions)

        return jsonify({"stocks": suggestions})

    except Exception as e:
        return jsonify({"error": str(e)}), 500

# ==== Root Route ====
@app.route("/")
def home():
    return "🚀 Hybrid Model API is running! Use /predict, /analyze-portfolio, and /extract-symbols"

# ==== Start Server ====
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
