"""Deterministic local provider. Contains no live API calls and stores no uploads."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json, os
class Handler(BaseHTTPRequestHandler):
 def do_POST(self):
  body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
  if os.getenv('STUB_FAIL')=='true':
   self.send_response(503); self.end_headers(); return
  if self.path=='/responses':
   result={'status':'completed','output':[{'type':'message','content':[{'type':'output_text','text':json.dumps({'items':[{'foodName':'Chicken breast, roasted','portionG':150,'confidence':0.82},{'foodName':'Rice, cooked','portionG':120,'confidence':0.67}]})}]}]}
  else:
   result={'foods':[{'fdcId':171477,'description':'Chicken breast, roasted (demo)','foodNutrients':[{'nutrientId':1008,'value':165},{'nutrientId':1003,'value':31},{'nutrientId':1004,'value':3.6},{'nutrientId':1005,'value':0}]},{'fdcId':168878,'description':'Rice, white, cooked (demo)','foodNutrients':[{'nutrientId':1008,'value':130},{'nutrientId':1003,'value':2.69},{'nutrientId':1004,'value':0.28},{'nutrientId':1005,'value':28.17}]}]}
  if self.path=='/foods/search' and 'rice' in body.get('query','').lower(): result['foods'].reverse()
  encoded=json.dumps(result).encode(); self.send_response(200); self.send_header('Content-Type','application/json'); self.send_header('Content-Length',str(len(encoded))); self.end_headers(); self.wfile.write(encoded)
 def log_message(self,*args): pass
ThreadingHTTPServer(('0.0.0.0',8090),Handler).serve_forever()
