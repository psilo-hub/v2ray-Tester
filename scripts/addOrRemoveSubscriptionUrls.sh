#!/bin/bash

# Change to the directory where the script is located
cd "$(dirname "$0")" || exit 1

# Ask user for action
echo "Choose an option:"
echo "1) add"
echo "2) remove"
read -p "Enter your choice (1 or 2): " choice

case "$choice" in
    1)
        action="--add"
        ;;
    2)
        action="--remove"
        ;;
    *)
        echo "Error: Invalid choice. Please enter 1 or 2."
        exit 1
        ;;
esac

# Ask for the text file (default: subscription_list.txt)
read -p "Enter text file path (default: subscription_list.txt): " filename
if [[ -z "$filename" ]]; then
    filename="subscription_list.txt"
fi

# Check if the text file exists
if [[ ! -f "$filename" ]]; then
    echo "Error: File '$filename' not found."
    exit 1
fi

# Check if test.jar exists (optional but helpful)
if [[ ! -f "test.jar" ]]; then
    echo "Error: test.jar not found in script directory."
    exit 1
fi

# Iterate over each line of the file and execute the jar
while IFS= read -r line || [[ -n "$line" ]]; do
    # Skip empty lines if desired? Not required, but can be added.
    # if [[ -z "$line" ]]; then continue; fi
    java -jar test.jar "$action" "$line"
done < "$filename"