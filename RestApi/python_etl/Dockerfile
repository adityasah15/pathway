FROM python:3.12-slim

WORKDIR /app
COPY python_etl/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY python_etl/analytics.py python_etl/analytics_app.py ./

ENV PYTHONUNBUFFERED=1
EXPOSE 5000
CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--workers", "1", "analytics_app:app"]
