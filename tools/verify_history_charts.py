"""Read-only chart parity audit against the existing MainViewModel arithmetic."""
import argparse,collections,datetime,hashlib,json,math,re,sqlite3,struct,subprocess
from pathlib import Path
ap=argparse.ArgumentParser();ap.add_argument('--database',type=Path,required=True);ap.add_argument('--report',type=Path);ap.add_argument('--cli',type=Path);args=ap.parse_args()
project=Path(__file__).resolve().parent.parent
before=hashlib.sha256(args.database.read_bytes()).hexdigest()
db=sqlite3.connect(f'file:{args.database.resolve().as_posix()}?mode=ro',uri=True);db.row_factory=sqlite3.Row
def cell(row,key,default=None):return row[key] if key in row.keys() and row[key] is not None else default
raw=[];tasks=[]
for row in db.execute('SELECT * FROM workout_tasks WHERE isCompleted=1 ORDER BY date DESC,id DESC'):
 t={k:str(cell(row,k,'')) for k in ['id','date','name','category','target','actualWeight','bodyPart']}
 t.update(logType=cell(row,'logType',0),unilateral=bool(cell(row,'isUnilateral',0)),sets=cell(row,'sets',''))
 raw.append(t);tasks.append(dict(t,sets=json.loads(t['sets'] or '[]')))
weights=[]
for row in db.execute('SELECT * FROM weight_records ORDER BY date DESC'):
 weights.append(dict(date=cell(row,'date',''),weight=cell(row,'weight',0),**{k:cell(row,k) for k in ['bodyFatKg','skeletalMuscleKg','bodyWaterPercentage','waistCircumference','hipCircumference']}))
try:
 row=db.execute('SELECT * FROM app_settings WHERE id=0').fetchone()
 profile={k:cell(row,k,0) for k in ['height','age','gender']} if row else dict(height=0,age=0,gender=0)
except sqlite3.OperationalError:profile=dict(height=0,age=0,gender=0)
payload=dict(tasks=raw,weights=weights,profile=profile)
cli=args.cli or project/'rust/history_core/target/release/history_cli.exe'
result=json.loads(subprocess.run([str(cli)],input=json.dumps(payload,ensure_ascii=False).encode(),capture_output=True,check=True).stdout)
charts=result['charts'];tasks.sort(key=lambda t:(t['date'],int(t['id'])))
def f(n):return struct.unpack('f',struct.pack('f',n))[0]
def n(s):
 m=re.search(r'[0-9]+(?:\.[0-9]+)?',str(s or ''))
 return f(float(m[0])) if m else 0.
def duration(s):
 s=str(s or '').lower();v=n(s)
 return f(v*60) if 'h' in s else f(v/60) if 's' in s and 'm' not in s else v
def grouped(rows,month):
 groups=collections.defaultdict(list)
 for date,value in rows:
  datetime.date.fromisoformat(date)
  groups[date[:7]+'-01' if month else date].append(value)
 return [{'date':k,'value':f(sum(v)/len(v))} for k,v in sorted(groups.items())]
point_count=0
def equal_series(actual,rows):
 global point_count
 for period,month in [('daily',False),('monthly',True)]:
  expected=grouped(rows,month);assert len(actual[period])==len(expected)
  for a,b in zip(actual[period],expected):
   assert a['date']==b['date']
   assert math.isclose(a['value'],b['value'],rel_tol=1e-7,abs_tol=1e-5),(a,b)
   point_count+=1
names=collections.defaultdict(list);categories=set();cardio=collections.defaultdict(float);heat=collections.defaultdict(float)
for t in tasks:
 names[t['name']].append(t);categories.add((t['category'],t['name']))
 if t['category']=='CARDIO':cardio[t['date']]+=sum(duration(s.get('weightOrDuration','')) for s in t['sets']) if t['sets'] else duration(t['target'])
 volume=0.
 for s in t['sets']:
  volume=f(volume+f(max(1,n(s.get('weightOrDuration')))*max(1,n(s.get('reps')))))
  rw,rr=n(s.get('rightWeight')),n(s.get('rightReps'))
  if t['unilateral'] and rw>0 and rr>0:volume=f(volume+f(rw*rr))
 if not t['sets']:volume=n(t['target']) if n(t['target'])>0 else 1.
 heat[t['bodyPart']]=f(heat[t['bodyPart']]+volume)
assert len(charts['exercises'])==len(categories)
for e in charts['exercises']:
 assert (e['category'],e['name']) in categories
 assert e['logType']==names[e['name']][0]['logType']
 assert e['unilateral']==any(t['unilateral'] for t in names[e['name']])
for name,rows in names.items():
 dates=collections.defaultdict(list)
 for t in rows:dates[t['date']].append(t)
 modes={key:[] for key in ['duration','weight','reps','rightWeight']}
 for date,records in sorted(dates.items()):
  sets=[]
  for t in records:sets.extend(t['sets'] or [dict(weightOrDuration=t['actualWeight'] or t['target'],reps=t['target'])])
  for key,value in [('duration',f(sum(duration(s.get('weightOrDuration')) for s in sets))),('weight',max([n(s.get('weightOrDuration')) for s in sets],default=0)),('reps',f(sum(n(s.get('reps')) for s in sets))),('rightWeight',max([n(s.get('rightWeight')) for s in sets],default=0))]:modes[key].append((date,value))
 for key,values in modes.items():equal_series(charts['exerciseSeries'][name][key],values)
equal_series(charts['cardio'],[(k,f(v)) for k,v in cardio.items()])
height=f(profile['height']);age=profile['age'];gender=profile['gender']
for key in ['weight','bmi','bmr','bodyFatKg','skeletalMuscleKg','bodyWaterPercentage','whr']:
 rows=[]
 for w in weights:
  weight=f(w['weight']);v=None
  if key=='weight':v=weight
  elif key=='bmi':v=0 if height<=0 else f(weight/f(f(height/100)*f(height/100)))
  elif key=='bmr':v=0 if height<=0 or age<=0 else f(f(f(f(10*weight)+f(6.25*height))-f(5*age))+(5 if gender==0 else -161))
  elif key=='whr':
   if w['waistCircumference'] is not None and w['hipCircumference'] is not None:v=f(f(w['waistCircumference'])/f(w['hipCircumference'])) if w['hipCircumference']>0 else 0
  elif w[key] is not None:v=f(w[key])
  if v is not None:rows.append((w['date'],v))
 equal_series(charts['body'][key],rows)
maximum=max(heat.values(),default=1)
assert len(heat)==len(charts['heatmap'])
for row in charts['heatmap']:
 assert math.isclose(row['volume'],heat[row['bodyPart']],rel_tol=1e-7,abs_tol=1e-5)
 assert math.isclose(row['intensity'],f(heat[row['bodyPart']]/maximum),rel_tol=1e-7,abs_tol=1e-6)
assert before==hashlib.sha256(args.database.read_bytes()).hexdigest()
report={'exercise_names':len(names),'category_choices':len(categories),'compared_chart_points':point_count,'body_series':7,'heatmap_parts':len(heat),'all_daily_and_monthly_points_match':True,'monthly_average_policy_preserved':True,'exercise_log_type_and_sides_match':True,'body_and_heatmap_match':True,'backup_unchanged':True}
if args.report:args.report.write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
