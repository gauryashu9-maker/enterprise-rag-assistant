import textwrap

DOCS = {
    "employee-handbook.pdf": (
        "Employee Handbook",
        [
            "Working hours are 9:00 to 18:00, Monday to Friday, with a one hour lunch break.",
            "The dress code is smart casual. Fridays are casual days.",
            "The company observes 12 public holidays per calendar year.",
            "New employees have a probation period of 3 months. The notice period after probation is 30 days.",
            "Employees must follow the code of conduct and report concerns to their manager or to HR.",
        ],
    ),
    "leave-policy.pdf": (
        "Leave Policy",
        [
            "Employees get 24 days of paid annual leave per calendar year. Up to 5 unused days can carry over.",
            "Sick leave is 10 days per year. A doctor's note is required after 2 consecutive days.",
            "Parental leave is 12 weeks and can be taken in one block or split with manager approval.",
            "Leave of more than 3 days must be requested from the manager at least 5 working days in advance.",
        ],
    ),
    "expense-policy.pdf": (
        "Expense Policy",
        [
            "Expense claims must be submitted within 30 days of purchase, with original receipts attached.",
            "Meals while travelling are reimbursed up to INR 1,500 per day.",
            "Travel is booked in economy class unless the manager approves otherwise.",
            "Claims above INR 25,000 need approval from the finance team in addition to the manager.",
        ],
    ),
    "onboarding-notes.txt": (
        "Onboarding Notes",
        [
            "A laptop is issued on the first day. It must be returned within 7 days after the last working day.",
            "Email and VPN accounts are created within 24 hours of joining.",
            "Each new employee gets a buddy for the first month.",
            "First-week training covers security awareness, the code of conduct and the tools used by your team.",
        ],
    ),
}

def escape(text):
    return text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

def make_pdf(path, title, paragraphs):
    lines = [title, ""]
    for paragraph in paragraphs:
        lines.extend(textwrap.wrap(paragraph, 88))
        lines.append("")
    ops = ["BT", "/F1 12 Tf", "15 TL", "50 790 Td"]
    for line in lines:
        ops.append("(" + escape(line) + ") Tj")
        ops.append("T*")
    ops.append("ET")
    stream = "\n".join(ops).encode("latin-1", "replace")
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n".encode()
    out += b"0000000000 65535 f \n"
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    with open(path, "wb") as handle:
        handle.write(out)

for name, (title, paragraphs) in DOCS.items():
    target = "docs/demo-documents/" + name
    if name.endswith(".pdf"):
        make_pdf(target, title, paragraphs)
    else:
        with open(target, "w", encoding="utf-8") as handle:
            handle.write(title + "\n\n" + "\n\n".join(paragraphs) + "\n")
    print("created", target)
