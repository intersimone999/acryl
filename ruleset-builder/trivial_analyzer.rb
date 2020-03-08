require "csv"

raise "Specify input/output file and mode [sin/seq]"   if ARGV.size != 3

INFILE, OUTFILE, MODE = *ARGV
MODE = MODE.downcase

raise "Output file already exists"      if FileTest.exists? OUTFILE
raise "Input file does not exist"       unless FileTest.exists? INFILE

dataset = {}
CSV.parse(File.read(INFILE), {headers: true, col_sep: ","}) do |row|
    check   = row["check"]
    api     = row["api"]
    app     = row["app"]
    
    api = api.to_s.split("&").sort if MODE == "seq"
    
    dataset[check] = {} unless dataset[check]
    dataset[check][api] = {occurrences: 0, involved_apps: []} unless dataset[check][api]
    
    dataset[check][api][:occurrences] += 1
    dataset[check][api][:involved_apps].push app unless dataset[check][api][:involved_apps].include?(app)
end

CSV.open OUTFILE, "w" do |csv|
    csv << ["check", "api", "occurrences", "reliability"]
    dataset.each do |check, apival|
        apival.each do |api, data|
            csv << [check, api, data[:occurrences], data[:involved_apps].size]
        end
    end
end
