# CampusFix Thesis — LaTeX Source (Politecnico di Torino `toptesi`)

Full draft of "Design and Development of an Offline-First Mobile
Application for Field Data Collection," built on the **official PoliTo
`toptesi` thesis class** — the same class used by the
[Politecnico di Torino Thesis Template](https://www.overleaf.com/latex/templates/politecnico-di-torino-thesis-template/cmpmxftwvvbr)
on Overleaf. It uses the class's own `\frontespizio` title page,
`\sommario` (abstract), `\ringraziamenti` (acknowledgements), and `\indici`
(table of contents / list of figures / list of tables), with front-page
fields set in `partials/properties.tex`.

## File layout

```
thesis-latex/
├── thesis.tex                 ← main file (compile this)
├── references.bib             ← bibliography (11 real references)
├── partials/
│   ├── properties.tex         ← title-page fields (\ateneo, \titolo, \relatore, …)
│   ├── glossary.tex           ← acronyms + glossary entries
│   ├── abstract.tex           ← abstract body (heading from \sommario)
│   ├── acknowledgements.tex   ← acknowledgements body (heading from \ringraziamenti)
│   ├── chapter_1.tex … chapter_6.tex
│   ├── appendix_a.tex         ← supplementary code listings
│   └── appendix_b.tex         ← SUS usability questionnaire
└── README.md
```

## How to compile

**Overleaf (recommended).** Create a blank project, upload every file
keeping the `partials/` subfolder, set **`thesis.tex`** as the main
document, and set the compiler to **pdfLaTeX** (Menu → Compiler). Overleaf
ships `toptesi` and runs bibtex + makeglossaries automatically. The
`toptesi` class is already installed on Overleaf, so no extra setup is
needed.

**Local (TeX Live full / MacTeX).** The `toptesi` package must be present
(`tlmgr install toptesi` if not). Then:
```
pdflatex thesis
bibtex thesis
makeglossaries thesis
pdflatex thesis
pdflatex thesis
```

## Before you submit

1. **Title page** (`partials/properties.tex`): replace `[Supervisor Name]`,
   the `[Defense Month]`, and the `\corsodilaurea` value if your programme
   name differs. Uncomment `\secondorelatore` / `\tutoreaziendale` if you
   have a co-supervisor or company tutor.
2. **Official logo.** The Overleaf PoliTo template ships a `logopolito`
   image. Add that file to the project and uncomment
   `%\logosede{logopolito}` in `properties.tex` to show it. Without it, the
   title page still compiles (just without the crest).
3. **Evaluation data** (`partials/chapter_5.tex`): every table/figure
   captioned *"Illustrative data — replace with measured values"* holds
   plausible placeholders, **not** real measurements. Run the six scenarios
   the chapter describes and substitute real numbers before submitting. The
   conflict-resolution result (§5.6) is the one exception — it is derived by
   tracing the actual `SyncManager` code rather than by execution.
4. **Dedication / acknowledgements** (`partials/acknowledgements.tex` and
   the dedication block in `thesis.tex`) are placeholders — personalize or
   remove them.
5. **Not yet compiled here.** This machine had no LaTeX toolchain, so
   balance, cross-references (`\label`/`\cref`), and citation keys were all
   verified by script, but the PDF itself was not rendered. **Compile once
   in Overleaf and skim the output** — in particular check that the wide
   TikZ diagrams (architecture, sequence, state machine) and the `longtable`
   requirement tables fit inside `toptesi`'s narrower text block, and nudge
   `scale=`/`width=` down if any overflow the margin.

## Notes on template fidelity

- Bibliography uses classic bibtex with `\bibliographystyle{plain}`. The
  stock PoliTo template default is `acm`; `plain` was chosen only because it
  renders every entry type (including `\url` in `@misc`) reliably. Change
  the one `\bibliographystyle` line in `thesis.tex` if you prefer `acm`.
- The class options (`a4paper,12pt,cucitura,tipotesi=magistrale,stile=classica`)
  match the one-sided master's-thesis configuration. For double-sided
  printing, use the commented `twoside` `\documentclass` line at the top of
  `thesis.tex`.
