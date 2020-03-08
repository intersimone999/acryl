# Merges the results from several CSV files coming from different projects into a single one.
# Input:
#   - 1: input folder
#   - 2: output file
# Output:
#   - Writes the result to (2)

require "csv"

raise "Wrong number of argument. Needed: (i) input folder, (ii) output filename" if ARGV.size != 2

infolder = ARGV[0]
outfile  = ARGV[1]

raise "Output file already exists"      if FileTest.exist? outfile
raise "Input folder does not exist"     if !FileTest.exist?(infolder) || !FileTest.directory?(infolder)

CSV.open outfile, "w" do |csv|
    csv << %w(id app version sdk_min sdk_trg check method apis apis_number message modified_files)
    Dir.glob("#{infolder}/*.csv").each do |fname|
        puts "Scanning #{fname}"
        CSV.parse(File.read(fname), {headers: true, col_sep: "\t", quote_char: '🍔'}) do |row|
            csv << [
                row["id"], 
                row["app"],
                row["version"],
                row["sdk_min"],
                row["sdk_trg"],
                row["check"],
                row["method"],
                row["apis"],
                row["apis"].to_s.split("&").size,
                row["message"],
                row["modified_files"]
            ]
        end
    end
end
