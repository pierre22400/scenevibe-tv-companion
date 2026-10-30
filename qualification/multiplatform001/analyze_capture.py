import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from campaign_analysis import detailed_analysis, markdown_report

"""Read complete public SceneVibe campaigns, preserving source digests and separating platforms.

Only supplied JSON events are counted. Overlapping captures are deduplicated by
canonical event bytes after JSON decoding. Human labels are comparison evidence,
not automatic detection input. No physical advertising verdict is inferred.
"""


def analyze(paths):
    """Decode all source lines, group the two authorized platforms and omit private/unknown fields."""
    return detailed_analysis(paths)


def main():
    """Analyze existing captures in read-only mode and write a separate UTF-8 report."""
    parser = argparse.ArgumentParser()
    parser.add_argument('inputs', type=Path, nargs='+')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--markdown', type=Path, help='Separate human-readable campaign report')
    args = parser.parse_args()
    if args.output.resolve() in [p.resolve() for p in args.inputs]:
        raise ValueError('Output must not replace a source log')
    if args.markdown and (args.markdown.resolve() in [p.resolve() for p in args.inputs] or args.markdown.resolve() == args.output.resolve()):
        raise ValueError('Markdown must not replace a source or the JSON report')
    report = analyze(args.inputs)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    if args.markdown:
        args.markdown.write_text(markdown_report(report), encoding='utf-8')


if __name__ == '__main__':
    main()
