param([Parameter(Mandatory)][string]$DocumentPath,[string]$QaPdfPath)
$ErrorActionPreference='Stop'
$manualDocument=$null
$manualWord=$null
try {
    $manualWord=New-Object -ComObject Word.Application
    $manualWord.Visible=$false
    $manualWord.DisplayAlerts=0
    $manualDocument=$manualWord.Documents.Open([IO.Path]::GetFullPath($DocumentPath),$false,$false)
    $manualDocument.Repaginate()
    $manualDocument.Fields.Update() | Out-Null
    foreach($manualToc in $manualDocument.TablesOfContents) {
        $manualToc.Update()
        $manualToc.Range.Font.Name='Calibri'
        $manualToc.Range.Font.Size=11
        $manualToc.Range.ParagraphFormat.SpaceBefore=0
        $manualToc.Range.ParagraphFormat.SpaceAfter=3
        $manualToc.Range.ParagraphFormat.LineSpacingRule=0
    }
    $manualDocument.Repaginate()
    foreach($manualToc in $manualDocument.TablesOfContents) { $manualToc.UpdatePageNumbers() }
    $manualHeadingPages=@{}
    foreach($manualParagraph in $manualDocument.Paragraphs) {
        if($manualParagraph.OutlineLevel -le 2) {
            $manualHeadingPages[$manualParagraph.Range.Text.Trim()]=[int]$manualParagraph.Range.Information(3)
        }
    }
    $manualTocChecks=0
    foreach($manualToc in $manualDocument.TablesOfContents) {
        foreach($manualEntry in ($manualToc.Range.Text -split "`r")) {
            $manualParts=$manualEntry.Trim() -split "`t"
            if($manualParts.Count -lt 2) { continue }
            $manualHeading=$manualParts[0].Trim()
            if($manualHeadingPages.ContainsKey($manualHeading)) {
                if([int]$manualParts[-1] -ne $manualHeadingPages[$manualHeading]) { throw "TOC page mismatch: $manualHeading" }
                $manualTocChecks++
            }
        }
    }
    if($manualTocChecks -lt 50) { throw "Incomplete TOC verification: $manualTocChecks" }
    $manualDocument.Save()
    if($QaPdfPath) { $manualDocument.ExportAsFixedFormat([IO.Path]::GetFullPath($QaPdfPath),17) }
    Write-Output ('WORD_PAGES='+$manualDocument.ComputeStatistics(2))
    Write-Output ('WORD_TOC='+$manualDocument.TablesOfContents.Count)
    Write-Output ('WORD_TOC_PAGE_CHECKS='+$manualTocChecks)
} finally {
    if($manualDocument) { $manualDocument.Close(0); [Runtime.InteropServices.Marshal]::FinalReleaseComObject($manualDocument) | Out-Null }
    if($manualWord) { $manualWord.Quit(); [Runtime.InteropServices.Marshal]::FinalReleaseComObject($manualWord) | Out-Null }
}
