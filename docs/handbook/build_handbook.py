"""Build the source-backed handbook; requires the bundled python-docx/Pillow runtime."""
from pathlib import Path
import re
import sys
from PIL import Image
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.enum.text import WD_ALIGN_PARAGRAPH

ROOT = Path(__file__).resolve().parent
OUTPUT = ROOT / 'Hieu_toan_bo_he_thong_Poker_20261011.docx'
SOURCE = ROOT / 'Hieu_toan_bo_he_thong_Poker.md'


def field(paragraph, instruction, placeholder=''):
    run = paragraph.add_run()
    for tag, value in [('begin', None), (None, instruction), ('separate', None)]:
        node = OxmlElement('w:instrText' if tag is None else 'w:fldChar')
        if tag is None:
            node.set(qn('xml:space'), 'preserve'); node.text = value
        else:
            node.set(qn('w:fldCharType'), tag)
        run._r.append(node)
    paragraph.add_run(placeholder)
    node = OxmlElement('w:fldChar'); node.set(qn('w:fldCharType'), 'end')
    paragraph.add_run()._r.append(node)


def table(doc, lines):
    rows = [[c.strip() for c in line.strip().strip('|').split('|')] for line in lines]
    rows = [row for row in rows if not all(re.fullmatch(r':?-+:?', c) for c in row)]
    assert len({len(row) for row in rows}) == 1
    t = doc.add_table(rows=1, cols=len(rows[0])); t.autofit = False
    widths = {2: [2.35, 4.15], 3: [1.55, 1.65, 3.3], 4: [2.8, 1.23, 1.23, 1.24]}[len(rows[0])]
    for col, width in zip(t.columns, widths): col.width = Inches(width)
    borders = OxmlElement('w:tblBorders')
    for side in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        node = OxmlElement(f'w:{side}')
        for key, val in [('val', 'single'), ('sz', '4'), ('color', 'D9D9D9')]: node.set(qn(f'w:{key}'), val)
        borders.append(node)
    t._tbl.tblPr.append(borders)
    for index, values in enumerate(rows):
        cells = t.rows[0].cells if index == 0 else t.add_row().cells
        tr_pr = cells[0]._tc.getparent().get_or_add_trPr()
        tr_pr.append(OxmlElement('w:cantSplit'))
        if index == 0: tr_pr.append(OxmlElement('w:tblHeader'))
        for cell, value, width in zip(cells, values, widths):
            cell.width = Inches(width)
            shading = OxmlElement('w:shd'); shading.set(qn('w:fill'), 'EAF0F6' if index == 0 else ('F7F8FA' if index % 2 == 0 else 'FFFFFF'))
            cell._tc.get_or_add_tcPr().append(shading)
            margins = OxmlElement('w:tcMar')
            for edge in ('top', 'bottom', 'left', 'right'):
                item = OxmlElement(f'w:{edge}'); item.set(qn('w:w'), '90'); item.set(qn('w:type'), 'dxa'); margins.append(item)
            cell._tc.get_or_add_tcPr().append(margins)
            p = cell.paragraphs[0]; p.paragraph_format.space_after = Pt(0); p.paragraph_format.line_spacing = 1.08
            run = p.add_run(value); run.font.size = Pt(10.5); run.bold = index == 0
    doc.add_paragraph().paragraph_format.space_after = Pt(0)


def numbered_list(doc, lines):
    numbering = doc.part.numbering_part.element
    source_id = doc.styles['List Number'].element.pPr.numPr.numId.val
    source_num = next(n for n in numbering.findall(qn('w:num')) if int(n.get(qn('w:numId'))) == source_id)
    abstract_id = source_num.find(qn('w:abstractNumId')).get(qn('w:val'))
    num_id = max(int(n.get(qn('w:numId'))) for n in numbering.findall(qn('w:num'))) + 1
    num = OxmlElement('w:num'); num.set(qn('w:numId'), str(num_id))
    abstract = OxmlElement('w:abstractNumId'); abstract.set(qn('w:val'), abstract_id); num.append(abstract)
    override = OxmlElement('w:lvlOverride'); override.set(qn('w:ilvl'), '0')
    start = OxmlElement('w:startOverride'); start.set(qn('w:val'), '1'); override.append(start); num.append(override)
    numbering.append(num)
    for line in lines:
        p = doc.add_paragraph(re.sub(r'^\d+\.\s+', '', line), 'List Number')
        props = p._p.get_or_add_pPr().get_or_add_numPr()
        props.get_or_add_ilvl().val = 0; props.get_or_add_numId().val = num_id


def build():
    assert not OUTPUT.exists() or '--qa-revision' in sys.argv, f'Refusing to overwrite {OUTPUT} without an explicit QA revision.'
    doc = Document(); section = doc.sections[0]
    section.page_width = Inches(8.5); section.page_height = Inches(11)
    section.left_margin = section.right_margin = Inches(1)
    section.top_margin = section.bottom_margin = Inches(.75)
    normal = doc.styles['Normal']; normal.font.name = 'Calibri'; normal.font.size = Pt(11.5)
    normal.paragraph_format.line_spacing = 1.16; normal.paragraph_format.space_after = Pt(7)
    for name, size in [('Title', 26), ('Heading 1', 20), ('Heading 2', 14), ('Heading 3', 12), ('Caption', 10.5)]:
        style = doc.styles[name]; style.font.name = 'Calibri'; style.font.size = Pt(size); style.font.color.rgb = RGBColor(0, 0, 0)
        style.font.bold = name.startswith('Heading')
        style.paragraph_format.keep_with_next = name != 'Caption'
        style.paragraph_format.space_before = Pt(12); style.paragraph_format.space_after = Pt(7)
    doc.styles['Heading 1'].paragraph_format.space_before = Pt(20)
    # The stock template has a decorative title rule; the handbook is plain text.
    for style in doc.styles:
        for border in list(style.element.iter(qn('w:pBdr'))): border.getparent().remove(border)
    for name in ('TOC 1', 'TOC 2', 'TOC 3'):
        if name in doc.styles:
            doc.styles[name].font.name = 'Calibri'; doc.styles[name].font.size = Pt(11)
    header = section.header.paragraphs[0]; header.add_run('POKER • CẨM NANG HỆ THỐNG').font.size = Pt(9)
    footer = section.footer.paragraphs[0]; footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    footer.add_run('Source 11/10/2026  •  '); field(footer, 'PAGE')
    lines = SOURCE.read_text(encoding='utf-8').splitlines(); i = 0; figure_count = 0; chapter_count = 0; toc_added = False
    while i < len(lines):
        line = lines[i]
        if not line.strip(): i += 1; continue
        if line.startswith('# '): doc.add_paragraph(line[2:], 'Title')
        elif line.startswith('## '):
            if not toc_added:
                doc.add_page_break(); p = doc.add_paragraph('Mục lục'); p.runs[0].font.size = Pt(20); p.runs[0].bold = True
                field(doc.add_paragraph(), 'TOC \\o "1-2" \\h \\z \\u', 'Mục lục được cập nhật bằng Word.')
                toc_added = True
                doc.add_page_break()
            doc.add_paragraph(line[3:], 'Heading 1'); chapter_count += 1
        elif line.startswith('### '): doc.add_paragraph(line[4:], 'Heading 2')
        elif line.startswith('|'):
            block = []
            while i < len(lines) and lines[i].startswith('|'): block.append(lines[i]); i += 1
            table(doc, block); continue
        elif re.match(r'^\d+\. ', line):
            block = []
            while i < len(lines) and re.match(r'^\d+\. ', lines[i]): block.append(lines[i]); i += 1
            numbered_list(doc, block); continue
        elif line.startswith('```'):
            i += 1; block = []
            while i < len(lines) and not lines[i].startswith('```'): block.append(lines[i]); i += 1
            p = doc.add_paragraph(); p.paragraph_format.line_spacing = 1.05; p.paragraph_format.space_after = Pt(8)
            run = p.add_run('\n'.join(block)); run.font.name = 'Consolas'; run.font.size = Pt(9.5)
        elif line.startswith('@figure '):
            name, caption = (part.strip() for part in line[8:].split('|', 1))
            path = ROOT / 'diagrams' / f'{name}.png'; assert path.is_file(), path
            with Image.open(path) as image: w, h = image.size
            height_limit = 4.4 if name == 'architecture' else 6.7
            width = min(6.5, height_limit * w / h)
            p = doc.add_paragraph(); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p.paragraph_format.keep_with_next = True
            shape = p.add_run().add_picture(str(path), width=Inches(width))
            shape._inline.docPr.set('descr', caption)
            figure_count += 1
            p = doc.add_paragraph(f'Hình {figure_count}. {caption}', 'Caption'); p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        else:
            doc.add_paragraph(line)
        i += 1
    settings = doc.settings.element
    update = OxmlElement('w:updateFields'); update.set(qn('w:val'), 'true'); settings.append(update)
    assert chapter_count == 18 and figure_count == 12
    doc.core_properties.title = 'Hiểu toàn bộ hệ thống Poker'
    doc.core_properties.subject = 'Cẩm nang source-backed dành cho người mới, không phải báo cáo học phần'
    doc.core_properties.author = 'Nhóm 1'; doc.core_properties.keywords = 'Java, TCP, Poker, JavaFX, JDBC, handbook'
    doc.save(OUTPUT)
    print(f'{OUTPUT}\nChapters={chapter_count}; Figures={figure_count}; Tables={len(doc.tables)}')


if __name__ == '__main__': build()
