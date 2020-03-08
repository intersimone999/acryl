# Aggregates the api file, measuring reliability (#apps) and #occurrences for each check
# Input:
#   - 1: input folder
#   - 2: output file
# Output:
#   - Writes the result to (2)


require "csv"
require "set"

raise "Specify input folder and output file"   if ARGV.size != 2

INFOLDER, OUTFILE = *ARGV

raise "Output file already exists"      if FileTest.exists? OUTFILE
raise "Input folder does not exist"       unless FileTest.exists? INFOLDER

dataset = {}
Dir.glob(File.join(INFOLDER, "APIs_*.csv")) do |infile|
    CSV.parse(File.read(infile), {headers: true, col_sep: "\t"}) do |row|
        app         = row["app"]
        apival      = row["api"]
        occurrences = row["occurrences"].to_i
            
        dataset[apival] = {occurrences: 0, involved_apps: Set.new} unless dataset[apival]
        
        dataset[apival][:occurrences] += occurrences
        dataset[apival][:involved_apps] = Set.new unless dataset[apival][:involved_apps]
        dataset[apival][:involved_apps].add app
    end
end

CSV.open OUTFILE, "w" do |csv|
    csv << ["api", "occurrences", "apps"]
    dataset.each do |api, data|
        csv << [api, data[:occurrences], data[:involved_apps].join("&")]
    end
end
