import argparse,collections,datetime,hashlib,json,math,re,sqlite3,subprocess
from pathlib import Path
parser=argparse.ArgumentParser(description='Read-only history snapshot and Rust parity verification; no private rows in report.')
parser.add_argument('--database',type=Path,required=True)
parser.add_argument('--cli',type=Path)
parser.add_argument('--report',type=Path)
args=parser.parse_args()
root=Path(__file__).resolve().parent.parent
backup=args.database
before=hashlib.sha256(backup.read_bytes()).hexdigest()
db=sqlite3.connect(f'file:{backup.resolve().as_posix()}?mode=ro',uri=True);db.row_factory=sqlite3.Row
rows=db.execute('SELECT * FROM workout_tasks WHERE isCompleted=1 ORDER BY date DESC,id DESC').fetchall()
raw=[];reference=[]
for t in rows:
 task={k:str(t[k] or '') for k in ['id','date','name','category','target','actualWeight']}
 task.update(logType=t['logType'] or 0,unilateral=bool(t['isUnilateral']),sets=t['sets'] or '')
 raw.append(task)
 parsed=[]
 for s in json.loads(t['sets'] or '[]'):
  parsed.append({k:None if s.get(k) is None else str(s[k]) for k in ['setNumber','weightOrDuration','reps','rightWeight','rightReps']})
 reference.append(dict(task,sets=parsed))
weights=[{'date':w['date'],'weight':w['weight']} for w in db.execute('SELECT date,weight FROM weight_records ORDER BY date DESC')]
payload={'tasks':raw,'weights':weights}
proc=subprocess.run([str(args.cli or root/'rust/history_core/target/release/history_cli.exe')],input=json.dumps(payload,ensure_ascii=False).encode(),capture_output=True,check=True)
out=json.loads(proc.stdout)
assert out['snapshot']['tasks']==reference,'Snapshot record mismatch'
assert len(weights)==len(out['snapshot']['weights'])
for a,b in zip(weights,out['snapshot']['weights']):assert a['date']==b['date'] and math.isclose(a['weight'],b['weight'],rel_tol=1e-12)

def number(text):
 text=str(text or '').strip()
 if not re.fullmatch(r'[0-9.]+',text):return None
 try:v=float(text)
 except ValueError:return None
 return v if math.isfinite(v) and v>=0 else None

def duration(text):
 text=str(text or '').strip().lower().replace(' ','')
 v=number(text)
 if v is not None:return v
 if ':' in text:
  parts=[number(p) for p in text.split(':')]
  if len(parts) not in (2,3) or any(v is None for v in parts) or any(v>=60 for v in parts[1:]):return None
  return parts[0]+parts[1]/60 if len(parts)==2 else parts[0]*60+parts[1]+parts[2]/60
 units={'minutes':1,'minute':1,'mins':1,'min':1,'分钟':1,'分':1,'hours':60,'hour':60,'hrs':60,'hr':60,'小时':60,'h':60,'seconds':1/60,'second':1/60,'secs':1/60,'sec':1/60,'秒':1/60,'m':1,'s':1/60}
 pattern=re.compile(r'([0-9.]+)('+ '|'.join(units)+r')')
 total=0;pos=0
 for match in pattern.finditer(text):
  if match.start()!=pos:return None
  n=number(match[1])
  if n is None:return None
  total+=n*units[match[2]];pos=match.end()
 return total if pos==len(text) and pos else None

def metric(tasks):
 m={'taskCount':len(tasks),'setCount':0,'reps':0.,'volumeKg':0.,'durationMinutes':0.,'maxWeightKg':0.,'trainingDays':len({t['date'] for t in tasks}),'legacyFallbackCount':0}
 for t in tasks:
  m['setCount']+=len(t['sets'])
  if not t['sets']:
   m['legacyFallbackCount']+=1
   if t['logType']==1:m['durationMinutes']+=duration(t['target']) or 0
   if t['logType']==0:m['maxWeightKg']=max(m['maxWeightKg'],number(t['actualWeight']) or 0)
  for s in t['sets']:
   for wk,rk in ([('weightOrDuration','reps'),('rightWeight','rightReps')] if t['unilateral'] else [('weightOrDuration','reps')]):
    if t['logType']==1:m['durationMinutes']+=duration(s[wk]) or 0
    else:
     n=number(s[rk]);m['reps']+=n or 0
     if t['logType']==0:
      w=number(s[wk]);m['maxWeightKg']=max(m['maxWeightKg'],w or 0)
      if w is not None and n is not None:m['volumeKg']+=w*n
 return m

def equal_metric(a,b):
 for k,v in a.items():assert math.isclose(v,b[k],rel_tol=1e-10,abs_tol=1e-8),(k,v,b[k])
equal_metric(metric(reference),out['summary'])
for name,key,groupfn in [('byDay','date',lambda t:t['date']),('byMonth','month',lambda t:t['date'][:7]),('byExercise','name',lambda t:t['name'])]:
 groups=collections.defaultdict(list)
 for t in reference:groups[groupfn(t)].append(t)
 assert len(groups)==len(out[name])
 for group in out[name]:equal_metric(metric(groups[group[key]]),group)

def legacy_number(s):
 match=re.search(r'[0-9]+(?:\.[0-9]+)?',s or '')
 return float(match[0]) if match else 0.
def legacy_duration(s):
 s=(s or '').lower();n=legacy_number(s)
 return n*60 if 'h' in s else n/60 if 's' in s and 'm' not in s else n
charts={}
for t in reference:
 key=(t['date'],t['name']);m=charts.setdefault(key,dict(durationMinutes=0.,maxWeightKg=0.,reps=0.,rightMaxWeightKg=0.))
 for s in t['sets'] or [{'weightOrDuration':t['actualWeight'] or t['target'],'reps':t['target'],'rightWeight':None}]:
  m['durationMinutes']+=legacy_duration(s['weightOrDuration']);m['maxWeightKg']=max(m['maxWeightKg'],legacy_number(s['weightOrDuration']))
  m['reps']+=legacy_number(s['reps']);m['rightMaxWeightKg']=max(m['rightMaxWeightKg'],legacy_number(s['rightWeight']))
for chart in out['legacyCharts']:equal_metric(charts[(chart['date'],chart['name'])],chart)
assert len(charts)==len(out['legacyCharts'])
assert before==hashlib.sha256(backup.read_bytes()).hexdigest()
summary={'snapshot_tasks':len(reference),'sets':sum(len(t['sets']) for t in reference),'weights':len(weights),'all_snapshot_fields_match':True,'all_daily_monthly_exercise_metrics_match':True,'all_legacy_chart_points_match':True,'backup_unchanged':True,'day_groups':len(out['byDay']),'month_groups':len(out['byMonth']),'exercise_groups':len(out['byExercise']),'warnings':len(out['warnings'])}
if args.report: args.report.write_text(json.dumps(summary,indent=2),encoding='utf-8')
print(json.dumps(summary,indent=2))
