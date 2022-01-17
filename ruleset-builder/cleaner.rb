# Cleans the input CSV from useless APIs (such as log functions)
# Input:
#   - 1: input CSV to clean
#   - 2: file with APIs to filer
#   - 3: output CSV
# Output:
#   - Writes the result to (3)

require "csv"

raise "Wrong number of argument. Needed: (i) input CSV, (ii) filter list, (iii) output filename" if ARGV.size != 3

infile  = ARGV[0]
filter  = ARGV[1]
outfile = ARGV[2]

filter  = File.join(File.dirname(__FILE__))unless FileTest.exist? filter

raise "Output file already exists"          if FileTest.exist? outfile
raise "Input CSV file does not exist"       if !FileTest.exist?(infile) || !FileTest.file?(infile)
raise "Input filter file does not exist"    if !FileTest.exist?(filter) || !FileTest.file?(filter)

filters = []
File.read(filter).split("\n").each do |pattern|
    filters.push Regexp.new(pattern)
end

deleted     = {}
l_delete    = 0

CSV.open outfile, "w" do |csv|
    csv << %w(id app version sdk_min sdk_trg check method apis apis_number message modified_files)
    CSV.parse(File.read(infile), headers: true, col_sep: ",") do |row|
        apis = row["apis"].to_s.split("&")
        
        one_deleted = false
        to_delete   = []
        apis.each do |api|
            filters.each do |f|
                if api =~ f
                    one_deleted = true
                    deleted[api] = 0 unless deleted[api]
                    deleted[api] += 1
                    
                    to_delete.push api
                end
            end
        end
        
        apis -= to_delete
        
        l_delete += 1 if one_deleted
        
        csv << [
            row["id"], 
            row["app"],
            row["version"],
            row["sdk_min"],
            row["sdk_trg"],
            row["check"],
            row["method"],
            apis.join("&"),
            apis.size,
            row["message"],
            row["modified_files"]
        ]
    end
end

deleted.each do |api, times|
    puts "Deleted #{api} #{times} times"
end

puts "Total lines affected: #{l_delete}"
