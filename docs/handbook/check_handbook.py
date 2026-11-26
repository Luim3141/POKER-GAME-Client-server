"""Small structural self-check; page PNG visual QA and Word pagination remain required."""
from pathlib import Path
import re
from zipfile import ZipFile
from docx import Document
from docx.oxml.ns import qn
from pypdf import PdfReader

root = Path(__file__).resolve().parent
document_path = root / 'Hieu_toan_bo_he_thong_Poker_20261011.docx'
doc = Document(document_path)
assert sum(p.style.name == 'Heading 1' for p in doc.paragraphs) == 18
assert sum(p.style.name == 'Heading 2' for p in doc.paragraphs) == 58
assert len(doc.inline_shapes) == 12 and len(doc.tables) == 11
assert all(shape._inline.docPr.get('descr') for shape in doc.inline_shapes)
assert [p.text.split('.')[0] for p in doc.paragraphs if p.style.name == 'Caption'] == [f'Hình {i}' for i in range(1, 13)]
assert len([p for p in doc.paragraphs if p.style.name == 'List Number']) == 18
list_ids = {p._p.pPr.numPr.numId.val for p in doc.paragraphs if p.style.name == 'List Number'}
assert len(list_ids) == 3
with ZipFile(document_path) as z:
    xml = z.read('word/document.xml').decode()
    assert 'TOC ' in xml and '_Toc' in xml and 'Mục lục được cập nhật bằng Word.' not in xml
assert not any(re.match(r'^\d+\. ', p.text) for p in doc.paragraphs if p.style.name == 'Normal')
assert all(t.rows[0]._tr.trPr.find(qn('w:tblHeader')) is not None for t in doc.tables)
qa = root.parents[1] / 'target' / 'handbook-qa-20261011-final'
pdf = PdfReader(qa / 'Hieu_toan_bo_he_thong_Poker_20261011.pdf')
assert len(pdf.pages) == 34 and len(list(qa.glob('page-*.png'))) == 34
assert len(PdfReader(root.parents[1] / 'target/handbook-qa-20261011-v3/Word_reference.pdf').pages) == 34
text = '\n'.join(p.text for p in doc.paragraphs)
assert '<redacted>' in text and 'PLAYER_AT_TABLE' in text
assert 'LobbyService' not in text and 'firewall-radmin-server.ps1' not in text
for diagram in root.joinpath('diagrams').glob('*.puml'):
    assert diagram.with_suffix('.png').is_file()
assert len(list(root.joinpath('diagrams').glob('*.puml'))) == 12
print('PASS: headings, populated TOC, 12 figures/alt text/captions, 11 repeated-header tables, 3 real lists, 34 rendered pages')
