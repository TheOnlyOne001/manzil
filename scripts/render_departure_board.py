"""Static, inspectable concept board; no map/API data is presented as live."""
from pathlib import Path
import html, re
import fitz

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'design'
OUT.mkdir(exist_ok=True)
INK='#142D4E'; BLUE='#2457D6'; TEAL='#087E83'; PAPER='#F5F7FA'; MUTED='#586B82'; BORDER='#DCE3ED'
parts=[]
def add(s): parts.append(s)
def rect(x,y,w,h,fill,rx=0,stroke='none',sw=1):
 add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>')
def text(x,y,t,size=16,color=INK,weight=400,anchor='start'):
 add(f'<text x="{x}" y="{y}" font-family="Arial, sans-serif" font-size="{size}" font-weight="{weight}" fill="{color}" text-anchor="{anchor}">{html.escape(t)}</text>')
def line(x1,y1,x2,y2,c=BORDER,w=1): add(f'<path d="M{x1} {y1}L{x2} {y2}" fill="none" stroke="{c}" stroke-width="{w}"/>')
def circle(x,y,r,c,stroke='none',sw=1): add(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{c}" stroke="{stroke}" stroke-width="{sw}"/>')
def icon(name,x,y,size=24,color=INK):
 p=ROOT/'design/assets/icons'/f'{name}.svg'
 if not p.exists(): return
 s=p.read_text(encoding='utf-8'); s=re.sub(r'<\?xml[^>]*>','',s); body=s[s.find('>')+1:s.rfind('</svg>')]
 add(f'<g transform="translate({x} {y}) scale({size/24})" fill="none" stroke="{color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">{body}</g>')
def panda(x,y,s=1):
 add(f'<g transform="translate({x} {y}) scale({s})">')
 circle(-20,-19,11,INK); circle(20,-19,11,INK)
 add(f'<ellipse cx="0" cy="0" rx="32" ry="27" fill="white" stroke="{INK}" stroke-width="1.6"/>')
 add(f'<ellipse cx="-12" cy="-2" rx="8" ry="10" transform="rotate(28 -12 -2)" fill="{INK}"/><ellipse cx="12" cy="-2" rx="8" ry="10" transform="rotate(-28 12 -2)" fill="{INK}"/>')
 circle(-10,-4,2.4,'white'); circle(10,-4,2.4,'white')
 add(f'<path d="M-4 9 Q0 5 4 9 Q0 15 -4 9 M-7 17 Q0 22 7 17" fill="{INK}" stroke="{INK}" stroke-width="1.3" stroke-linecap="round"/>')
 add('</g>')
def button(x,y,w,label,ico=None):
 rect(x,y,w,54,BLUE,16)
 text(x+w/2,y+34,label,16,'white',700,'middle')
 if ico: icon(ico,x+20,y+15,24,'white')
def phone(x,step,label):
 text(x,108,f'{step}  {label}',18,INK,700)
 rect(x,130,360,780,'white',32,BORDER,1.5)
 text(x+24,162,'6:20',13,INK,700)
 rect(x+300,151,22,10,INK,3); rect(x+324,154,2,4,INK,1)
 rect(x+136,890,88,4,INK,2)

add('<svg xmlns="http://www.w3.org/2000/svg" width="1240" height="1010" viewBox="0 0 1240 1010"><title>Manzil departure planning concept</title><desc>Three Android screens for capturing journey constraints, comparing departure times, and reviewing a selected journey. All estimates are illustrative.</desc>')
rect(0,0,1240,1010,PAPER)
text(40,49,'Manzil',30,INK,700)
text(150,49,'A calmer way to get there.',24,INK)
text(40,76,'Feature 1 · Departure planning · Native Android direction',15,MUTED)
for x,n,label in [(40,'1','Tell Panda'),(440,'2','Choose when'),(840,'3','Review & go')]:phone(x,n,label)

# Screen one: conversation becomes visible structured constraints.
x=40
panda(x+38,207,.57);text(x+68,213,'Panda',18,INK,700);text(x+282,213,'English',13,MUTED)
text(x+24,270,'Where are we',27,INK,700);text(x+24,305,'going today?',27,INK,700)
text(x+24,339,'Tell me what matters for this trip.',15,MUTED)
rect(x+24,368,312,102,'#EEF3FC',20)
text(x+42,397,'“Chennai Central before 8.',17,INK)
text(x+42,424,'Keep it under 100 rupees.',17,INK)
text(x+42,451,'I can walk 500 metres.”',17,INK)
circle(x+180,519,29,BLUE);icon('mic',x+168,507,24,'white')
text(x+180,567,'Tap to speak · or type instead',13,MUTED,400,'middle')
text(x+24,611,'Your trip',17,INK,700);text(x+295,611,'Edit',14,BLUE,700)
for yy,ic,lab,val in [(644,'map-pin','To','Chennai Central'),(691,'clock','Arrive by','8:00 pm'),(738,'wallet','Budget','Under ₹100'),(785,'footprints','Walking','Up to 500 m')]:
 icon(ic,x+24,yy-18,20,MUTED);text(x+55,yy,lab,14,MUTED);text(x+312,yy,val,14,INK,700,'end')
 if yy<785:line(x+55,yy+13,x+312,yy+13)
button(x+24,818,312,'Compare departures')

# Screen two: list rhythm, a single selected recommendation.
x=440
icon('arrow-left',x+22,194);text(x+62,213,'Chennai Central',18,INK,700)
text(x+24,266,'A little earlier.',27,INK,700);text(x+24,301,'Less time travelling.',25,INK,700)
panda(x+43,349,.52);text(x+70,346,'Leave at 6:35 pm to spend',14,INK);text(x+70,367,'20 fewer minutes on the road.',14,INK)
text(x+24,414,'Leave',13,MUTED);text(x+151,414,'Journey',13,MUTED);text(x+310,414,'Arrive',13,MUTED,400,'end')
rect(x+16,432,328,102,'#EDF3FF',16,BLUE,1.5)
text(x+32,461,'Recommended',12,BLUE,700)
text(x+32,500,'6:35',29,INK,700);text(x+103,500,'pm',13,MUTED)
text(x+156,494,'40 min',19,INK,700);text(x+312,494,'7:15 pm',17,INK,700,'end')
for yy,dep,dur,arr in [(570,'6:50','50 min','7:40 pm'),(664,'7:00','60 min','8:00 pm')]:
 text(x+32,yy+25,dep,29,INK,700);text(x+103,yy+25,'pm',13,MUTED)
 text(x+156,yy+19,dur,19,INK,700);text(x+312,yy+19,arr,17,INK,700,'end');line(x+24,yy+50,x+336,yy+50)
text(x+24,753,'Illustrative estimates',13,MUTED,700)
text(x+24,776,'Actual traffic can change your arrival.',13,MUTED)
button(x+24,818,312,'Review 6:35 pm journey')

# Screen three: schematic route, quiet sheet and compact facts.
x=840
icon('arrow-left',x+22,194);text(x+62,213,'Your journey',18,INK,700)
rect(x+16,236,328,269,'#EDF2F5',18)
for yy in [278,326,375,429,473]:line(x+24,yy,x+336,yy-25,'white',9)
for xx in [60,119,197,269]:line(x+xx,246,x+xx+24,494,'white',8)
add(f'<path d="M{x+50} 451 L{x+115} 424 L{x+119} 344 L{x+232} 344 L{x+276} 285" fill="none" stroke="white" stroke-width="12" stroke-linejoin="round"/><path d="M{x+50} 451 L{x+115} 424 L{x+119} 344 L{x+232} 344 L{x+276} 285" fill="none" stroke="{BLUE}" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>')
circle(x+50,451,7,'white',BLUE,3);circle(x+276,285,7,BLUE)
rect(x+158,245,169,30,'white',8);text(x+170,265,'Chennai Central',13,INK,700)
rect(x+28,464,110,26,'white',7);text(x+39,482,'Schematic map',11,MUTED)
panda(x+300,454,.68)
text(x+24,549,'Leave at 6:35 pm',26,INK,700)
text(x+24,578,'Estimated arrival 7:15 pm',16,MUTED)
line(x+24,601,x+336,601)
for xx,ic,val,lab in [(24,'clock','40 min','Journey'),(137,'wallet','₹65','Estimate'),(245,'footprints','420 m','Walking')]:
 icon(ic,x+xx,620,20,TEAL);text(x+xx,669,val,23,INK,700);text(x+xx,693,lab,12,MUTED)
rect(x+24,718,312,62,'#EFF7F5',12);icon('check',x+38,737,20,TEAL)
text(x+69,741,'Fits your budget and walking limit.',13,INK)
text(x+69,762,'Illustrative plan · Check before leaving.',11,MUTED)
text(x+180,805,'Save journey',14,BLUE,700,'middle')
button(x+24,818,312,'Start journey','navigation')

text(40,952,'Design concept · Illustrative estimates · No live routing',15,MUTED)
text(40,978,'Source icons: Lucide (ISC). Original vector panda study. Screen text uses a portable preview font.',12,MUTED)
add('</svg>')
svg='\n'.join(parts)
(OUT/'departure-planning-board.svg').write_text(svg,encoding='utf-8')
doc=fitz.open(stream=svg.encode(),filetype='svg'); pdf=fitz.open('pdf',doc.convert_to_pdf());pdf[0].get_pixmap(matrix=fitz.Matrix(1.5,1.5)).save(OUT/'departure-planning-board.png')
print(OUT/'departure-planning-board.png')
